package fr.sesame.questions;

import static fr.sesame.FabriquePdf.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sesame.FauxClaude;
import fr.sesame.FauxClaude.Cite;
import fr.sesame.FauxClaude.Texte;
import fr.sesame.SocleIntegration;
import fr.sesame.comptes.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Réponse en flux, citations revérifiées, historique des conversations */
class QuestionsTest extends SocleIntegration {

    private static final String TELETRAVAIL = "Le télétravail est limité à deux jours par semaine.";
    private static final String INDEMNITE = "Une indemnité de télétravail de 2,70 euros est versée par jour.";
    private static final String QUESTION = "Combien de jours de télétravail par semaine ?";

    private UUID karim;
    private String jeton;
    private UUID document;

    @BeforeEach
    void installer() throws Exception {
        karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        jeton = porteur(karim);
        document = deposer(karim, "Politique de télétravail", pdf(TELETRAVAIL, INDEMNITE), "RH");
    }

    // ==================== FLUX ET CITATIONS ====================

    @Test
    @DisplayName("la réponse arrive en flux : texte, citation avec document, page et extrait, puis fin")
    void reponseEnFlux() throws Exception {
        CLAUDE.jouer(new Texte("Le télétravail est limité "), new Texte("à deux jours par semaine."),
                new Cite(0, TELETRAVAIL));

        mvc.perform(post("/api/questions").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", QUESTION))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"));
        Flux flux = poser(jeton, QUESTION);

        assertThat(flux.evenements()).extracting(Evenement::nom).containsExactly("texte", "texte", "citation", "fin");
        JsonNode citation = flux.de("citation").get(0);
        assertThat(citation.get("numero").asInt()).isEqualTo(1);
        assertThat(citation.get("documentId").asText()).isEqualTo(document.toString());
        assertThat(citation.get("titre").asText()).isEqualTo("Politique de télétravail");
        assertThat(citation.get("page").asInt()).isEqualTo(1);
        assertThat(citation.get("extrait").asText()).isEqualTo(TELETRAVAIL);
        assertThat(flux.fin().get("texte").asText()).isEqualTo("Le télétravail est limité à deux jours par semaine. [1]");
        assertThat(flux.fin().get("sansReponse").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("la requête envoyée au modèle : consigne fixe, passages en documents à citer, question, aucun outil")
    void requeteEnvoyeeAuModele() throws Exception {
        poser(jeton, QUESTION);

        JsonNode requete = json.readTree(CLAUDE.derniereRequete());
        assertThat(requete.get("model").asText()).isEqualTo("claude-haiku-5-5");
        assertThat(requete.get("system").asText()).isEqualTo(ClientClaude.CONSIGNE);
        assertThat(requete.get("stream").asBoolean()).isTrue();
        assertThat(requete.at("/output_config/effort").asText()).isEqualTo("low");
        // Ni outil ni réglage que ce modèle refuse
        assertThat(requete.has("tools")).isFalse();
        assertThat(requete.has("temperature")).isFalse();
        assertThat(requete.get("messages")).hasSize(1);
        JsonNode contenu = requete.at("/messages/0/content");
        assertThat(requete.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(FauxClaude.documents(requete)).containsExactlyInAnyOrder(TELETRAVAIL, INDEMNITE);
        for (int i = 0; i < contenu.size() - 1; i++) {
            assertThat(contenu.get(i).get("type").asText()).isEqualTo("document");
            assertThat(contenu.get(i).at("/citations/enabled").asBoolean()).isTrue();
            assertThat(contenu.get(i).get("title").asText()).startsWith("Politique de télétravail (page ");
        }
        assertThat(contenu.get(contenu.size() - 1).get("text").asText()).isEqualTo(QUESTION);
        assertThat(CLAUDE.clesRecues()).containsOnly("cle-de-test");
    }

    @Test
    @DisplayName("sans passage pertinent, la réponse convenue est rendue sans appeler le modèle")
    void aucunPassagePertinent() throws Exception {
        Flux flux = poser(jeton, "Quelle est la capitale de l'Australie ?");

        assertThat(CLAUDE.requetes()).isEmpty();
        assertThat(flux.evenements()).extracting(Evenement::nom).containsExactly("fin");
        assertThat(flux.fin().get("texte").asText()).isEqualTo(ClientClaude.SANS_REPONSE);
        assertThat(flux.fin().get("sansReponse").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("une réponse que le modèle n'appuie sur aucune citation est remplacée par la réponse convenue")
    void reponseSansCitation() throws Exception {
        CLAUDE.jouer(new Texte("Le télétravail est illimité, d'après mes connaissances générales."));

        Flux flux = poser(jeton, QUESTION);

        assertThat(flux.de("citation")).isEmpty();
        assertThat(flux.fin().get("texte").asText()).isEqualTo(ClientClaude.SANS_REPONSE);
        assertThat(flux.fin().get("sansReponse").asBoolean()).isTrue();
        assertThat(sql.queryForObject("SELECT reponse FROM message", String.class)).isEqualTo(ClientClaude.SANS_REPONSE);
    }

    @Test
    @DisplayName("une citation est rejetée si elle vise un passage non transmis ou un texte absent du passage")
    void citationsReverifiees() throws Exception {
        CLAUDE.jouer(requete -> {
            int deux = FauxClaude.documents(requete).indexOf(TELETRAVAIL);
            return List.of(
                    new Texte("Affirmation un."), new Cite(7, TELETRAVAIL),
                    new Texte(" Affirmation deux."), new Cite(-1, TELETRAVAIL),
                    new Texte(" Affirmation trois."), new Cite(deux, "Le télétravail est illimité."),
                    new Texte(" Affirmation quatre."), new Cite(deux, ""),
                    new Texte(" Affirmation cinq."), new Cite(deux, "limité à deux jours"));
        });

        Flux flux = poser(jeton, QUESTION);

        assertThat(flux.de("citation")).hasSize(1);
        assertThat(flux.de("citation").get(0).get("extrait").asText()).isEqualTo(TELETRAVAIL);
        assertThat(flux.fin().get("texte").asText()).endsWith("Affirmation cinq. [1]").doesNotContain("[2]");
        assertThat(sql.queryForObject("SELECT count(*) FROM citation", Integer.class)).isEqualTo(1);
        assertThat(sql.queryForObject(
                "SELECT details->>'citationsRejetees' FROM journal_audit WHERE action = 'QUESTION_POSEE'", String.class))
                .isEqualTo("4");
    }

    @Test
    @DisplayName("deux passages cités reçoivent deux numéros ; le même passage cité deux fois garde le sien")
    void numerotationDesCitations() throws Exception {
        CLAUDE.jouer(requete -> {
            List<String> documents = FauxClaude.documents(requete);
            int un = documents.indexOf(TELETRAVAIL);
            int deux = documents.indexOf(INDEMNITE);
            return List.of(new Texte("Deux jours."), new Cite(un, TELETRAVAIL), new Cite(un, TELETRAVAIL),
                    new Texte(" Indemnité de 2,70 euros."), new Cite(deux, INDEMNITE),
                    new Texte(" Deux jours, donc."), new Cite(un, TELETRAVAIL));
        });

        Flux flux = poser(jeton, QUESTION);

        assertThat(flux.fin().get("texte").asText())
                .isEqualTo("Deux jours. [1] Indemnité de 2,70 euros. [2] Deux jours, donc. [1]");
        assertThat(flux.de("citation")).extracting(c -> c.get("numero").asInt()).containsExactly(1, 2, 1);
        assertThat(flux.de("citation")).extracting(c -> c.get("page").asInt()).containsExactly(1, 2, 1);
    }

    @Test
    @DisplayName("un refus du modèle donne la réponse convenue")
    void refusDuModele() throws Exception {
        CLAUDE.finirPar("refusal");

        assertThat(poser(jeton, QUESTION).fin().get("sansReponse").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("une panne de l'API donne un événement d'erreur sans détail technique, et rien n'est enregistré")
    void panneDeLApi() throws Exception {
        CLAUDE.repondreEnErreur(500);

        Flux flux = poser(jeton, QUESTION);

        assertThat(flux.evenements()).extracting(Evenement::nom).containsExactly("erreur");
        assertThat(flux.corps()).contains("La rédaction de la réponse a échoué").doesNotContain("panne simulée");
        assertThat(sql.queryForObject("SELECT count(*) FROM message", Integer.class)).isZero();
    }

    // ==================== CONTRÔLES D'ENTRÉE ====================

    @Test
    @DisplayName("une question vide, trop longue ou mal formée est refusée en 400 avant tout appel au modèle")
    void questionInvalide() throws Exception {
        for (String corps : List.of("{}", "{\"question\":\"  \"}", "{\"question\":\"\\u0000\\u0000\"}",
                "{\"question\":\"" + "a".repeat(501) + "\"}", "{\"question\":\"x\",\"conversationId\":\"pas-un-uuid\"}")) {
            mvc.perform(post("/api/questions").header("Authorization", jeton)
                    .contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest());
        }
        assertThat(CLAUDE.requetes()).isEmpty();
    }

    @Test
    @DisplayName("au-delà de 20 questions par minute, le compte reçoit 429 et le modèle n'est plus appelé")
    void limiteDeDebit() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(poser(jeton, QUESTION).statut()).isEqualTo(200);
        }

        mvc.perform(post("/api/questions").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", QUESTION))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mvc.perform(post("/api/recherche").header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", QUESTION))))
                .andExpect(status().isTooManyRequests());
        assertThat(CLAUDE.requetes()).hasSize(20);
        // La limite est propre à chaque compte
        String autre = porteur(compte("ines@exemple.test", Role.LECTEUR, "RH"));
        assertThat(poser(autre, QUESTION).statut()).isEqualTo(200);
    }

    // ==================== HISTORIQUE ====================

    @Test
    @DisplayName("la question, la réponse et ses citations sont enregistrées dans l'historique de l'auteur")
    void historique() throws Exception {
        Flux premiere = poser(jeton, QUESTION);
        String conversation = premiere.fin().get("conversationId").asText();
        Flux seconde = poser(jeton, Map.of("question", "Quelle indemnité de télétravail ?", "conversationId", conversation));

        assertThat(seconde.fin().get("conversationId").asText()).isEqualTo(conversation);
        mvc.perform(get("/api/conversations").header("Authorization", jeton))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titre").value(QUESTION));
        mvc.perform(get("/api/conversations/" + conversation).header("Authorization", jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].question").value(QUESTION))
                .andExpect(jsonPath("$.messages[0].reponse").value(premiere.fin().get("texte").asText()))
                .andExpect(jsonPath("$.messages[0].citations[0].numero").value(1))
                .andExpect(jsonPath("$.messages[0].citations[0].titre").value("Politique de télétravail"))
                .andExpect(jsonPath("$.messages[0].citations[0].extrait").exists());
    }

    @Test
    @DisplayName("chaque question est envoyée seule au modèle : les échanges précédents n'y figurent pas")
    void questionsIndependantes() throws Exception {
        String conversation = poser(jeton, QUESTION).fin().get("conversationId").asText();

        poser(jeton, Map.of("question", "Quelle indemnité de télétravail ?", "conversationId", conversation));

        JsonNode requete = json.readTree(CLAUDE.derniereRequete());
        assertThat(requete.get("messages")).hasSize(1);
        assertThat(CLAUDE.derniereRequete()).doesNotContain(QUESTION).doesNotContain("la réponse est la suivante");
    }

    @Test
    @DisplayName("la conversation d'un autre utilisateur répond 404 en lecture, en suppression et en poursuite")
    void conversationDUnAutre() throws Exception {
        String conversation = poser(jeton, QUESTION).fin().get("conversationId").asText();
        String ines = porteur(compte("ines@exemple.test", Role.LECTEUR, "RH"));
        String inexistante = mvc.perform(get("/api/conversations/" + UUID.randomUUID()).header("Authorization", ines))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/conversations").header("Authorization", ines)).andExpect(jsonPath("$.length()").value(0));
        assertThat(mvc.perform(get("/api/conversations/" + conversation).header("Authorization", ines))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString()).isEqualTo(inexistante);
        mvc.perform(delete("/api/conversations/" + conversation).header("Authorization", ines))
                .andExpect(status().isNotFound());
        assertThat(poser(ines, Map.of("question", QUESTION, "conversationId", conversation)).statut()).isEqualTo(404);
        assertThat(sql.queryForObject("SELECT count(*) FROM message", Integer.class)).isEqualTo(1);

        mvc.perform(delete("/api/conversations/" + conversation).header("Authorization", jeton))
                .andExpect(status().isNoContent());
        assertThat(sql.queryForObject("SELECT count(*) FROM message", Integer.class)).isZero();
    }

    @Test
    @DisplayName("après le retrait d'un droit, l'historique ne rend plus ni le titre ni l'extrait de la source")
    void historiqueApresRevocation() throws Exception {
        String conversation = poser(jeton, QUESTION).fin().get("conversationId").asText();
        String admin = porteur(compte("admin@exemple.test", Role.ADMINISTRATEUR));

        mvc.perform(put("/api/admin/documents/" + document + "/groupes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"groupes\":[]}")).andExpect(status().isOk());

        String historique = mvc.perform(get("/api/conversations/" + conversation).header("Authorization", jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].citations.length()").value(0))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(historique).doesNotContain("Politique de télétravail").doesNotContain(TELETRAVAIL);
    }

    @Test
    @DisplayName("la question est consignée avec les documents transmis au modèle et ceux qu'il a cités")
    void questionConsignee() throws Exception {
        poser(jeton, QUESTION);

        JsonNode details = json.readTree(sql.queryForObject(
                "SELECT details::text FROM journal_audit WHERE action = 'QUESTION_POSEE'", String.class));
        assertThat(details.get("question").asText()).isEqualTo(QUESTION);
        assertThat(details.get("documentsTransmis").toString()).isEqualTo("[\"Politique de télétravail\"]");
        assertThat(details.get("documentsCites").toString()).isEqualTo("[\"Politique de télétravail\"]");
        assertThat(details.get("sansReponse").asBoolean()).isFalse();
    }
}

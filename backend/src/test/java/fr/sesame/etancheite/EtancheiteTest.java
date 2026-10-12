package fr.sesame.etancheite;

import static fr.sesame.FabriquePdf.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sesame.comptes.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Suite d'étanchéité : chaque couple utilisateur-document, sur chaque route.
 *
 * Le résultat attendu ne vient pas de l'application : il est recalculé ici, en Java,
 * à partir de la seule règle « un utilisateur voit un document s'il partage un groupe
 * avec lui ». Six comptes, six documents et six routes donnent 216 vérifications.
 */
class EtancheiteTest extends SocleEtancheite {

    private record Compte(String nom, Role role, Set<String> groupes) {
    }

    private record Doc(String titre, String marqueur, Set<String> groupes) {

        String texte() {
            return "Note interne. Le code de référence de ce document est " + marqueur + ".";
        }

        String question() {
            return "Quel est le code de référence " + marqueur + " ?";
        }
    }

    private static final List<Compte> COMPTES = List.of(
            new Compte("lea", Role.LECTEUR, Set.of()),
            new Compte("karim", Role.CONTRIBUTEUR, Set.of("RH")),
            new Compte("ines", Role.CONTRIBUTEUR, Set.of("Finance")),
            new Compte("hugo", Role.ADMINISTRATEUR, Set.of("Direction")),
            new Compte("nadia", Role.LECTEUR, Set.of("RH", "Finance")),
            new Compte("admin-sans-groupe", Role.ADMINISTRATEUR, Set.of()));

    private static final List<Doc> DOCUMENTS = List.of(
            new Doc("Guide commun", "alphabravo", Set.of("Tous")),
            new Doc("Grille salariale", "charliedelta", Set.of("RH")),
            new Doc("Budget", "echofoxtrot", Set.of("Finance")),
            new Doc("Comité de direction", "golfhotel", Set.of("Direction")),
            new Doc("Plan de réorganisation", "indiajuliett", Set.of("RH", "Direction")),
            new Doc("Brouillon sans groupe", "kilolima", Set.of()));

    private final Map<String, String> jetons = new LinkedHashMap<>();
    private final Map<String, UUID> documents = new LinkedHashMap<>();

    @BeforeEach
    void installer() throws Exception {
        for (Compte compte : COMPTES) {
            UUID id = compte(compte.nom() + "@exemple.test", compte.role(), compte.groupes().toArray(String[]::new));
            jetons.put(compte.nom(), porteur(id));
        }
        // Les dépôts sont faits par un compte à part, membre de tous les groupes
        UUID deposant = compte("deposant@exemple.test", Role.CONTRIBUTEUR, "RH", "Finance", "Direction");
        for (Doc doc : DOCUMENTS) {
            String[] groupes = doc.groupes().isEmpty() ? new String[] {"Tous"} : doc.groupes().toArray(String[]::new);
            UUID id = deposer(deposant, doc.titre(), pdf(doc.texte()), groupes);
            if (doc.groupes().isEmpty()) {
                sql.update("DELETE FROM document_groupe WHERE document_id = ?", id);
            }
            documents.put(doc.titre(), id);
        }
        sql.update("DELETE FROM utilisateur WHERE email = 'deposant@exemple.test'");
    }

    /** La règle d'accès, écrite indépendamment de l'application */
    private static boolean doitVoir(Compte compte, Doc doc) {
        Set<String> groupesDuCompte = new java.util.HashSet<>(compte.groupes());
        groupesDuCompte.add("Tous");
        return !Collections.disjoint(groupesDuCompte, doc.groupes());
    }

    @TestFactory
    @DisplayName("chaque couple utilisateur-document, sur chaque route")
    List<DynamicTest> matrice() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Compte compte : COMPTES) {
            for (Doc doc : DOCUMENTS) {
                boolean attendu = doitVoir(compte, doc);
                for (Route route : Route.values()) {
                    String nom = compte.nom() + " × " + doc.titre() + " × " + route.name().toLowerCase()
                            + (attendu ? " : visible" : " : invisible");
                    tests.add(dynamicTest(nom, () -> {
                        UUID document = documents.get(doc.titre());
                        assertThat(atteint(route, jetons.get(compte.nom()), document, passageDe(document), doc.question()))
                                .isEqualTo(attendu);
                    }));
                }
            }
        }
        return tests;
    }

    @Test
    @DisplayName("la matrice contient bien des cas visibles et des cas interdits pour chaque compte")
    void matriceNonTriviale() {
        for (Compte compte : COMPTES) {
            assertThat(DOCUMENTS).anyMatch(doc -> doitVoir(compte, doc)).anyMatch(doc -> !doitVoir(compte, doc));
        }
        assertThat(DOCUMENTS).noneMatch(doc -> doc.groupes().isEmpty() && COMPTES.stream().anyMatch(c -> doitVoir(c, doc)));
    }

    @Test
    @DisplayName("la recherche ne rend jamais un passage hors des documents visibles, quelle que soit la question")
    void rechercheBorneeAuxDocumentsVisibles() throws Exception {
        List<String> questions = new ArrayList<>(DOCUMENTS.stream().map(Doc::question).toList());
        questions.add("code de référence de ce document");
        questions.add("note interne");
        for (Compte compte : COMPTES) {
            Set<String> visibles = new java.util.HashSet<>();
            for (Doc doc : DOCUMENTS) {
                if (doitVoir(compte, doc)) {
                    visibles.add(documents.get(doc.titre()).toString());
                }
            }
            for (String question : questions) {
                JsonNode corps = json.readTree(chercher(jetons.get(compte.nom()), question).corps());
                assertThat(corps.findValuesAsText("documentId")).isSubsetOf(visibles);
                // Ni titre ni extrait d'un document interdit dans la réponse
                for (Doc doc : DOCUMENTS) {
                    if (!doitVoir(compte, doc)) {
                        assertThat(corps.toString()).doesNotContain(doc.titre());
                        assertThat(corps.findValuesAsText("extrait")).noneMatch(e -> e.contains(doc.marqueur()));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("non-interférence : supprimer ou ajouter des documents interdits ne change pas un octet des réponses")
    void nonInterference() throws Exception {
        String lea = jetons.get("lea");
        String question = "Quel est le code de référence charliedelta de la grille salariale ?";
        Reponse recherche = chercher(lea, question);
        Reponse liste = appeler(lea, "/api/documents");

        // Tous les documents que Léa ne voit pas disparaissent, puis un nouveau document interdit arrive
        sql.update("DELETE FROM document WHERE id NOT IN (SELECT document_id FROM document_groupe dg "
                + "JOIN groupe g ON g.id = dg.groupe_id WHERE g.nom = 'Tous')");
        assertThat(chercher(lea, question)).isEqualTo(recherche);
        assertThat(appeler(lea, "/api/documents")).isEqualTo(liste);

        UUID karim = sql.queryForObject("SELECT id FROM utilisateur WHERE email = 'karim@exemple.test'", UUID.class);
        sql.update("UPDATE utilisateur SET role = 'CONTRIBUTEUR' WHERE id = ?", karim);
        deposer(karim, "Nouvelle grille salariale", pdf("Le code de référence charliedelta de la grille salariale."), "RH");
        assertThat(chercher(lea, question)).isEqualTo(recherche);
        assertThat(appeler(lea, "/api/documents")).isEqualTo(liste);
    }

    @Test
    @DisplayName("un extrait ne s'obtient que sous son propre document : pas d'accès par un identifiant voisin")
    void extraitCroise() throws Exception {
        String karim = jetons.get("karim");
        UUID guide = documents.get("Guide commun");
        UUID grille = documents.get("Grille salariale");
        UUID budget = documents.get("Budget");
        Reponse inexistant = appeler(karim, "/api/documents/" + UUID.randomUUID() + "/passages/" + UUID.randomUUID());

        assertThat(appeler(karim, "/api/documents/" + grille + "/passages/" + passageDe(grille)).statut()).isEqualTo(200);
        // Passage d'un document visible demandé sous un autre document visible
        assertThat(appeler(karim, "/api/documents/" + guide + "/passages/" + passageDe(grille))).isEqualTo(inexistant);
        // Passage d'un document interdit demandé sous un document visible
        assertThat(appeler(karim, "/api/documents/" + guide + "/passages/" + passageDe(budget))).isEqualTo(inexistant);
        assertThat(appeler(karim, "/api/documents/" + budget + "/passages/" + passageDe(guide))).isEqualTo(inexistant);
    }

    @Test
    @DisplayName("un document dont l'indexation n'est pas terminée n'est ni cherchable ni citable")
    void documentNonIndexe() throws Exception {
        String karim = jetons.get("karim");
        UUID grille = documents.get("Grille salariale");
        UUID passage = passageDe(grille);
        String question = "Quel est le code de référence charliedelta ?";
        assertThat(atteint(Route.RECHERCHE, karim, grille, passage, question)).isTrue();

        // Posé directement en base : par l'application, un document en échec n'a aucun passage
        sql.update("UPDATE document SET etat = 'EN_ECHEC' WHERE id = ?", grille);

        assertThat(atteint(Route.RECHERCHE, karim, grille, passage, question)).isFalse();
        assertThat(atteint(Route.EXTRAIT, karim, grille, passage, question)).isFalse();
        // Son état reste consultable par ceux qui ont le droit de le voir
        assertThat(atteint(Route.DETAIL, karim, grille, passage, question)).isTrue();
    }

    @ParameterizedTest(name = "une question hostile est traitée comme du texte : {0}")
    @ValueSource(strings = {
            "'; DROP TABLE document; --",
            "salaire' OR '1'='1",
            "charliedelta & ! | ( ) :* <-> ''",
            "\\ \u0000 %s %n {{7*7}} ${jndi:ldap://exemple.test/a}",
            "le la les de du des",
            "[1,2,3]::vector"
    })
    void questionHostile(String question) throws Exception {
        for (String compte : List.of("lea", "karim")) {
            Reponse reponse = chercher(jetons.get(compte), question);
            assertThat(reponse.statut()).as(reponse.corps()).isEqualTo(200);
            assertThat(json.readTree(reponse.corps()).findValuesAsText("documentId"))
                    .doesNotContain(documents.get("Budget").toString(), documents.get("Comité de direction").toString());
        }
        assertThat(sql.queryForObject("SELECT count(*) FROM document", Integer.class)).isEqualTo(DOCUMENTS.size());
    }

    @Test
    @DisplayName("une question vide ou trop longue est refusée, et la limite de résultats est bornée")
    void demandeDeRechercheInvalide() throws Exception {
        String lea = jetons.get("lea");
        for (String corps : List.of("{}", "{\"question\":\"   \"}", "{\"question\":\"" + "a".repeat(501) + "\"}",
                "{\"question\":\"budget\",\"limite\":1000}", "{\"question\":\"budget\",\"limite\":0}")) {
            mvc.perform(post("/api/recherche").header("Authorization", lea)
                    .contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("chaque recherche est consignée avec sa question et les documents rendus")
    void rechercheConsignee() throws Exception {
        sql.update("DELETE FROM journal_audit");

        chercher(jetons.get("karim"), "Quel est le code de référence charliedelta ?");

        JsonNode details = json.readTree(sql.queryForObject(
                "SELECT details::text FROM journal_audit WHERE action = 'RECHERCHE' AND acteur_email = 'karim@exemple.test'",
                String.class));
        assertThat(details.get("question").asText()).isEqualTo("Quel est le code de référence charliedelta ?");
        assertThat(details.get("documents").toString()).contains("Grille salariale").doesNotContain("Budget");
    }
}

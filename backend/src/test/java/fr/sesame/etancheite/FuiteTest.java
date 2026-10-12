package fr.sesame.etancheite;

import static fr.sesame.FabriquePdf.pdf;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sesame.FauxClaude;
import fr.sesame.FauxClaude.Cite;
import fr.sesame.FauxClaude.Texte;
import fr.sesame.comptes.Role;
import fr.sesame.questions.ClientClaude;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de fuite : la réponse à la question n'existe que dans un document interdit.
 *
 * La preuve porte sur deux choses : ce que l'utilisateur reçoit, et le contenu exact
 * de la requête HTTP envoyée au modèle, capturée par le faux serveur Claude. Si un
 * passage interdit n'atteint jamais le modèle, aucune formulation de question et
 * aucune défaillance du modèle ne peuvent le faire sortir.
 */
class FuiteTest extends SocleEtancheite {

    private static final String QUESTION = "Quel est le salaire d'un développeur senior ?";
    private static final String GRILLE = "Grille confidentielle. Le salaire d'un développeur senior va de 52 000 à 61 000 euros.";
    private static final String GUIDE = "Guide d'accueil. Le salaire est versé le 28 du mois. Un développeur senior accompagne chaque nouvel arrivant.";
    private static final String CODIR = "Comité de direction. Le salaire du futur développeur senior de l'agence de Bordeaux est gelé.";
    private static final List<String> SECRETS = List.of("52 000", "61 000", "Grille", "confidentielle", "Bordeaux",
            "Comité de direction", "gelé");

    private String lea;
    private String karim;
    private UUID grille;

    @BeforeEach
    void installer() throws Exception {
        lea = porteur(compte("lea@exemple.test", Role.LECTEUR));
        UUID idKarim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        karim = porteur(idKarim);
        UUID hugo = compte("hugo@exemple.test", Role.CONTRIBUTEUR, "Direction");
        deposer(idKarim, "Guide d'accueil", pdf(GUIDE), "Tous");
        grille = deposer(idKarim, "Grille salariale", pdf(GRILLE), "RH");
        deposer(hugo, "Compte rendu du comité de direction", pdf(CODIR), "Direction");
    }

    @Test
    @DisplayName("la requête envoyée au modèle pour un lecteur ne contient aucun passage ni titre interdit")
    void requeteAuModeleSansPassageInterdit() throws Exception {
        Flux flux = poser(lea, QUESTION);

        // Le modèle a bien été appelé, avec le seul document que Léa peut voir
        assertThat(CLAUDE.requetes()).hasSize(1);
        assertThat(FauxClaude.documents(CLAUDE.derniereRequete())).containsExactly(GUIDE);
        assertThat(CLAUDE.derniereRequete()).doesNotContain(SECRETS);
        assertThat(flux.corps()).doesNotContain(SECRETS);
        assertThat(flux.de("citation")).allMatch(citation -> citation.get("titre").asText().equals("Guide d'accueil"));
    }

    @Test
    @DisplayName("contrôle : la même question, posée par un membre du groupe RH, transmet bien la grille au modèle")
    void memeQuestionAvecLeDroit() throws Exception {
        CLAUDE.jouer(requete -> List.of(new Texte("De 52 000 à 61 000 euros."),
                new Cite(FauxClaude.documents(requete).indexOf(GRILLE), GRILLE)));

        Flux flux = poser(karim, QUESTION);

        assertThat(FauxClaude.documents(CLAUDE.derniereRequete())).containsExactlyInAnyOrder(GRILLE, GUIDE);
        assertThat(CLAUDE.derniereRequete()).doesNotContain("Bordeaux").doesNotContain("Comité de direction");
        assertThat(flux.fin().get("texte").asText()).isEqualTo("De 52 000 à 61 000 euros. [1]");
        assertThat(flux.de("citation").get(0).get("documentId").asText()).isEqualTo(grille.toString());
    }

    @Test
    @DisplayName("quand le modèle n'a pas de quoi répondre, l'utilisateur reçoit la réponse convenue")
    void reponseConvenue() throws Exception {
        CLAUDE.jouer(new Texte(ClientClaude.SANS_REPONSE));

        Flux flux = poser(lea, QUESTION);

        assertThat(flux.fin().get("texte").asText()).isEqualTo(ClientClaude.SANS_REPONSE);
        assertThat(flux.fin().get("sansReponse").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("non-interférence : avec ou sans les documents interdits, la requête au modèle et la réponse sont identiques")
    void nonInterference() throws Exception {
        Flux avec = poser(lea, QUESTION);
        String requeteAvec = CLAUDE.derniereRequete();

        sql.update("DELETE FROM document WHERE titre <> 'Guide d''accueil'");
        Flux sans = poser(lea, QUESTION);

        assertThat(CLAUDE.derniereRequete()).isEqualTo(requeteAvec);
        assertThat(sans.evenements().stream().filter(e -> !e.nom().equals("fin")).toList())
                .isEqualTo(avec.evenements().stream().filter(e -> !e.nom().equals("fin")).toList());
        assertThat(sans.fin().get("texte")).isEqualTo(avec.fin().get("texte"));
    }

    @Test
    @DisplayName("un utilisateur sans aucun passage pertinent reçoit la même réponse, que le document interdit existe ou non")
    void aucunIndiceDExistence() throws Exception {
        sql.update("DELETE FROM document WHERE titre = 'Guide d''accueil'");

        Flux avec = poser(lea, QUESTION);
        sql.update("DELETE FROM document");
        Flux sans = poser(lea, QUESTION);

        assertThat(CLAUDE.requetes()).isEmpty();
        assertThat(avec.fin().get("texte").asText()).isEqualTo(ClientClaude.SANS_REPONSE);
        assertThat(sans.fin().get("texte")).isEqualTo(avec.fin().get("texte"));
        assertThat(sans.fin().get("sansReponse")).isEqualTo(avec.fin().get("sansReponse"));
    }

    @Test
    @DisplayName("un modèle qui citerait un passage interdit est contredit par le serveur : la citation est rejetée")
    void citationDUnPassageInterdit() throws Exception {
        // Le pire cas : le modèle « connaît » le texte interdit et tente de le citer
        CLAUDE.jouer(new Texte("De 52 000 à 61 000 euros."), new Cite(0, GRILLE), new Cite(1, GRILLE), new Cite(5, GRILLE));

        Flux flux = poser(lea, QUESTION);

        assertThat(flux.de("citation")).isEmpty();
        assertThat(flux.fin().get("texte").asText()).isEqualTo(ClientClaude.SANS_REPONSE);
        assertThat(sql.queryForObject("SELECT count(*) FROM citation", Integer.class)).isZero();
        JsonNode details = json.readTree(sql.queryForObject(
                "SELECT details::text FROM journal_audit WHERE action = 'QUESTION_POSEE'", String.class));
        assertThat(details.get("documentsTransmis").toString()).isEqualTo("[\"Guide d'accueil\"]");
        assertThat(details.get("citationsRejetees").asInt()).isEqualTo(3);
    }
}

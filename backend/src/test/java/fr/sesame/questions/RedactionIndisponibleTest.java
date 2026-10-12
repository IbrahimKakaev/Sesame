package fr.sesame.questions;

import static fr.sesame.FabriquePdf.pdf;
import static org.assertj.core.api.Assertions.assertThat;

import fr.sesame.SocleIntegration;
import fr.sesame.comptes.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Sans clé d'API : la recherche fonctionne, la rédaction répond 503 */
@TestPropertySource(properties = "sesame.llm.cle-api=")
class RedactionIndisponibleTest extends SocleIntegration {

    @Test
    @DisplayName("sans clé d'API, une question qui demanderait le modèle répond 503 ; les autres reçoivent la réponse convenue")
    void sansCleDApi() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        deposer(karim, "Télétravail", pdf("Le télétravail est limité à deux jours par semaine."), "RH");

        Flux pertinente = poser(porteur(karim), "Combien de jours de télétravail par semaine ?");
        Flux horsSujet = poser(porteur(karim), "Quelle est la capitale de l'Australie ?");

        assertThat(pertinente.statut()).isEqualTo(503);
        assertThat(horsSujet.fin().get("texte").asText()).isEqualTo(ClientClaude.SANS_REPONSE);
        assertThat(chercherStatut(porteur(karim))).isEqualTo(200);
        assertThat(CLAUDE.requetes()).isEmpty();
    }

    private int chercherStatut(String jeton) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/recherche")
                        .header("Authorization", jeton).contentType("application/json")
                        .content("{\"question\":\"télétravail\"}"))
                .andReturn().getResponse().getStatus();
    }
}

package fr.sesame.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.sesame.SocleIntegration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Données de démonstration : les quatre comptes et les dix documents de l'entreprise fictive */
@TestPropertySource(properties = "sesame.demo.charger=true")
class ChargementDemoTest extends SocleIntegration {

    @Autowired
    private ChargementDemo chargement;

    @BeforeEach
    void charger() {
        chargement.run(null);
        await().atMost(Duration.ofSeconds(30)).until(() -> sql.queryForObject(
                "SELECT count(*) FROM document WHERE etat IN ('EN_ATTENTE', 'EN_COURS')", Integer.class) == 0);
    }

    private List<String> titresVisibles(String email) throws Exception {
        String jeton = corps(mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "motDePasse", "demo-sesame-2026"))))
                .andExpect(status().isOk()).andReturn()).get("jetonAcces").asText();
        return json.readerForListOf(String.class).readValue(corps(mvc.perform(get("/api/documents")
                .header("Authorization", "Bearer " + jeton)).andReturn()).findValues("titre").toString());
    }

    @Test
    @DisplayName("les dix documents sont déposés et tous indexés, document piégé compris")
    void documentsIndexes() {
        assertThat(sql.queryForList("SELECT etat FROM document", String.class))
                .hasSize(10).containsOnly("INDEXE");
        assertThat(sql.queryForObject("SELECT count(*) FROM passage", Integer.class)).isGreaterThan(20);
        assertThat(sql.queryForObject("SELECT count(DISTINCT page) FROM passage", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("chaque compte de démonstration ne voit que les documents de ses groupes")
    void quatreComptesAuxDroitsDifferents() throws Exception {
        List<String> communs = List.of("Guide d'accueil des nouveaux salariés", "Charte informatique",
                "Note de service : travaux du restaurant d'entreprise", "Procédure de notes de frais");

        assertThat(titresVisibles("lea.martin@helianthe.example")).containsExactlyInAnyOrderElementsOf(communs);
        assertThat(titresVisibles("karim.benali@helianthe.example")).containsAll(communs)
                .contains("Politique de télétravail", "Grille salariale 2026", "Plan de formation 2026").hasSize(7);
        assertThat(titresVisibles("ines.roux@helianthe.example")).containsAll(communs)
                .contains("Budget prévisionnel 2027", "Procédure de clôture comptable mensuelle").hasSize(6);
        // L'administrateur n'est pas membre du groupe RH : la grille salariale lui échappe
        assertThat(titresVisibles("hugo.lefevre@helianthe.example")).containsAll(communs)
                .contains("Compte rendu du comité de direction du 14 septembre 2026").hasSize(5);
    }

    @Test
    @DisplayName("un second démarrage ne recharge rien")
    void chargementUneSeuleFois() throws Exception {
        chargement.run(null);

        assertThat(sql.queryForObject("SELECT count(*) FROM document", Integer.class)).isEqualTo(10);
        assertThat(sql.queryForObject("SELECT count(*) FROM utilisateur", Integer.class)).isEqualTo(4);
        mvc.perform(get("/api/moi")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "hugo.lefevre@helianthe.example", "motDePasse", "demo-sesame-2026"))))
                .andExpect(jsonPath("$.utilisateur.role").value("ADMINISTRATEUR"));
    }
}

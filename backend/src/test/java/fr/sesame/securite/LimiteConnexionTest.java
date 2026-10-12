package fr.sesame.securite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.sesame.SocleIntegration;
import fr.sesame.comptes.Role;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Limite de débit sur la connexion : 5 essais par e-mail et 20 par adresse, par quart d'heure */
class LimiteConnexionTest extends SocleIntegration {

    private ResultActions connecter(String email, String motDePasse, String adresse) throws Exception {
        return mvc.perform(post("/api/auth/connexion")
                .with(requete -> {
                    requete.setRemoteAddr(adresse);
                    return requete;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "motDePasse", motDePasse))));
    }

    @Test
    @DisplayName("après 5 échecs sur un e-mail, même le bon mot de passe est refusé en 429")
    void limiteParEmail() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);
        for (int i = 0; i < 5; i++) {
            connecter("lea@exemple.test", "essai-numero-" + i, "198.51.100." + i).andExpect(status().isUnauthorized());
        }

        connecter("lea@exemple.test", MOT_DE_PASSE, "198.51.100.99")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("le blocage d'un e-mail ne touche pas les autres comptes")
    void autresComptesEpargnes() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);
        compte("karim@exemple.test", Role.LECTEUR);
        for (int i = 0; i < 6; i++) {
            connecter("lea@exemple.test", "mauvais-mot-de-passe", "198.51.100.1");
        }

        connecter("karim@exemple.test", MOT_DE_PASSE, "198.51.100.1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("une connexion réussie remet le compteur de l'e-mail à zéro")
    void succesRemetAZero() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);
        for (int tour = 0; tour < 2; tour++) {
            for (int i = 0; i < 4; i++) {
                connecter("lea@exemple.test", "mauvais-mot-de-passe", "198.51.100.1")
                        .andExpect(status().isUnauthorized());
            }
            connecter("lea@exemple.test", MOT_DE_PASSE, "198.51.100.1").andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("une même adresse qui essaie beaucoup d'e-mails est bloquée après 20 essais")
    void limiteParAdresse() throws Exception {
        for (int i = 0; i < 20; i++) {
            connecter("cible" + i + "@exemple.test", "mauvais-mot-de-passe", "203.0.113.7")
                    .andExpect(status().isUnauthorized());
        }

        connecter("cible20@exemple.test", "mauvais-mot-de-passe", "203.0.113.7")
                .andExpect(status().isTooManyRequests());
        // Une autre adresse n'est pas touchée
        connecter("cible21@exemple.test", "mauvais-mot-de-passe", "203.0.113.8")
                .andExpect(status().isUnauthorized());
    }
}

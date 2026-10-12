package fr.sesame.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.sesame.SocleIntegration;
import fr.sesame.comptes.Role;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Connexion, renouvellement des jetons et déconnexion */
class AuthentificationTest extends SocleIntegration {

    private static final String COOKIE = ControleurAuthentification.COOKIE;

    private ResultActions connecter(String email, String motDePasse) throws Exception {
        return mvc.perform(post("/api/auth/connexion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "motDePasse", motDePasse))));
    }

    private Cookie cookieDeSession(String email) throws Exception {
        return connecter(email, MOT_DE_PASSE).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
    }

    private ResultActions renouveler(Cookie cookie) throws Exception {
        return mvc.perform(post("/api/auth/renouvellement").cookie(cookie));
    }

    @Test
    @DisplayName("une connexion réussie rend un jeton d'accès utilisable et le profil du compte")
    void connexionReussie() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR, "RH");

        MvcResult resultat = connecter("lea@exemple.test", MOT_DE_PASSE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur.email").value("lea@exemple.test"))
                .andExpect(jsonPath("$.utilisateur.role").value("LECTEUR"))
                .andExpect(jsonPath("$.utilisateur.groupes[*].nom").value(org.hamcrest.Matchers.contains("RH", "Tous")))
                .andExpect(jsonPath("$.expireDans").value(600))
                .andReturn();

        String jeton = corps(resultat).get("jetonAcces").asText();
        mvc.perform(get("/api/moi").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lea@exemple.test"));
        assertThat(actionsAudit()).containsExactly("CONNEXION_REUSSIE");
    }

    @Test
    @DisplayName("le jeton de renouvellement part dans un cookie HttpOnly, Secure, SameSite=Strict, jamais dans le corps")
    void cookieDeRenouvellementProtege() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);

        MvcResult resultat = connecter("lea@exemple.test", MOT_DE_PASSE)
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().secure(COOKIE, true))
                .andExpect(cookie().sameSite(COOKIE, "Strict"))
                .andExpect(cookie().path(COOKIE, "/api/auth"))
                .andReturn();

        String valeur = resultat.getResponse().getCookie(COOKIE).getValue();
        String corps = resultat.getResponse().getContentAsString();
        assertThat(corps).doesNotContain(valeur).doesNotContain("motDePasse").doesNotContain("$2");
        // En base, seule l'empreinte du jeton est conservée
        assertThat(sql.queryForList("SELECT empreinte FROM jeton_renouvellement", String.class))
                .hasSize(1).doesNotContain(valeur);
    }

    @Test
    @DisplayName("l'e-mail est insensible à la casse et aux espaces")
    void emailNormalise() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);

        connecter("  Lea@Exemple.TEST ", MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("mot de passe faux, e-mail inconnu et compte désactivé donnent exactement le même refus")
    void refusIndiscernables() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);
        UUID desactive = compte("ancien@exemple.test", Role.LECTEUR);
        sql.update("UPDATE utilisateur SET actif = false WHERE id = ?", desactive);

        String mauvaisMotDePasse = connecter("lea@exemple.test", "pas-le-bon-mot-de-passe")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String emailInconnu = connecter("personne@exemple.test", MOT_DE_PASSE)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String compteDesactive = connecter("ancien@exemple.test", MOT_DE_PASSE)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(mauvaisMotDePasse).isEqualTo(emailInconnu).isEqualTo(compteDesactive);
        assertThat(sql.queryForObject("SELECT count(*) FROM jeton_renouvellement", Integer.class)).isZero();
        assertThat(actionsAudit()).containsOnly("CONNEXION_ECHOUEE").hasSize(3);
    }

    @Test
    @DisplayName("une demande de connexion incomplète est refusée en 400")
    void demandeIncomplete() throws Exception {
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("le renouvellement rend un nouveau jeton d'accès et remplace le cookie")
    void renouvellement() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);
        Cookie premier = cookieDeSession("lea@exemple.test");

        MvcResult resultat = renouveler(premier).andExpect(status().isOk()).andReturn();

        Cookie second = resultat.getResponse().getCookie(COOKIE);
        assertThat(second.getValue()).isNotEqualTo(premier.getValue());
        mvc.perform(get("/api/moi").header("Authorization", "Bearer " + corps(resultat).get("jetonAcces").asText()))
                .andExpect(status().isOk());
        renouveler(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un jeton de renouvellement rejoué est refusé et révoque toute la session")
    void rejeuDetecte() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);
        Cookie premier = cookieDeSession("lea@exemple.test");
        Cookie second = renouveler(premier).andReturn().getResponse().getCookie(COOKIE);

        // Le premier jeton a déjà servi : quelqu'un le rejoue
        renouveler(premier).andExpect(status().isUnauthorized());

        // Le jeton légitime issu de la même connexion est révoqué lui aussi
        renouveler(second).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("le renouvellement est refusé sans cookie, avec un cookie inventé ou expiré")
    void renouvellementRefuse() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);

        mvc.perform(post("/api/auth/renouvellement")).andExpect(status().isUnauthorized());
        renouveler(new Cookie(COOKIE, "jeton-invente")).andExpect(status().isUnauthorized());

        Cookie expire = cookieDeSession("lea@exemple.test");
        sql.update("UPDATE jeton_renouvellement SET expire_le = now() - interval '1 second'");
        renouveler(expire).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un compte désactivé ne peut plus renouveler sa session")
    void renouvellementCompteDesactive() throws Exception {
        UUID lea = compte("lea@exemple.test", Role.LECTEUR);
        Cookie cookie = cookieDeSession("lea@exemple.test");
        sql.update("UPDATE utilisateur SET actif = false WHERE id = ?", lea);

        renouveler(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la déconnexion révoque la session et efface le cookie")
    void deconnexion() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);
        Cookie cookie = cookieDeSession("lea@exemple.test");

        mvc.perform(post("/api/auth/deconnexion").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));

        renouveler(cookie).andExpect(status().isUnauthorized());
    }
}

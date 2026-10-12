package fr.sesame.etancheite;

import static fr.sesame.FabriquePdf.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.sesame.comptes.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Révocation : un droit retiré l'est à la requête suivante, sur toutes les routes,
 * avec le jeton d'accès que l'utilisateur possédait déjà.
 */
class RevocationTest extends SocleEtancheite {

    private static final String QUESTION = "Quel est le salaire du développeur senior charliedelta ?";

    private UUID karim;
    private String jetonKarim;
    private String jetonAdmin;
    private UUID grille;
    private UUID passage;

    @BeforeEach
    void installer() throws Exception {
        karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        jetonKarim = porteur(karim);
        jetonAdmin = porteur(compte("admin@exemple.test", Role.ADMINISTRATEUR));
        groupe("Finance");
        grille = deposer(karim, "Grille salariale", pdf("Le salaire du développeur senior est charliedelta."), "RH");
        passage = passageDe(grille);
        assertThat(accesDeKarim()).doesNotContainValue(false);
    }

    private Map<Route, Boolean> accesDeKarim() throws Exception {
        return acces(jetonKarim, grille, passage, QUESTION);
    }

    private void modifierKarim(boolean actif, String... groupes) throws Exception {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("nom", "Karim");
        corps.put("role", Role.CONTRIBUTEUR);
        corps.put("actif", actif);
        corps.put("groupes", List.of(groupes).stream().map(this::groupe).toList());
        mvc.perform(put("/api/admin/utilisateurs/" + karim).header("Authorization", jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("retirer l'utilisateur du groupe ferme toutes les routes dès la requête suivante")
    void retraitDuGroupe() throws Exception {
        modifierKarim(true, "Finance");

        assertThat(accesDeKarim()).doesNotContainValue(true);
    }

    @Test
    @DisplayName("retirer le groupe du document ferme toutes les routes dès la requête suivante")
    void retraitDuDroitSurLeDocument() throws Exception {
        mvc.perform(put("/api/admin/documents/" + grille + "/groupes").header("Authorization", jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("groupes", List.of(groupe("Finance"))))))
                .andExpect(status().isOk());

        assertThat(accesDeKarim()).doesNotContainValue(true);
    }

    @Test
    @DisplayName("supprimer le groupe ferme toutes les routes dès la requête suivante")
    void suppressionDuGroupe() throws Exception {
        mvc.perform(delete("/api/admin/groupes/" + groupe("RH")).header("Authorization", jetonAdmin))
                .andExpect(status().isNoContent());

        assertThat(accesDeKarim()).doesNotContainValue(true);
    }

    @Test
    @DisplayName("supprimer le document ferme toutes les routes dès la requête suivante")
    void suppressionDuDocument() throws Exception {
        mvc.perform(delete("/api/admin/documents/" + grille).header("Authorization", jetonAdmin))
                .andExpect(status().isNoContent());

        assertThat(acces(jetonKarim, grille, passage, QUESTION)).doesNotContainValue(true);
    }

    @Test
    @DisplayName("un compte désactivé reçoit 401 partout, même avec un jeton encore valide")
    void desactivationDuCompte() throws Exception {
        modifierKarim(false, "RH");

        assertThat(appeler(jetonKarim, "/api/documents").statut()).isEqualTo(401);
        assertThat(appeler(jetonKarim, "/api/documents/" + grille).statut()).isEqualTo(401);
        assertThat(appeler(jetonKarim, "/api/documents/" + grille + "/fichier").statut()).isEqualTo(401);
        assertThat(appeler(jetonKarim, "/api/documents/" + grille + "/passages/" + passage).statut()).isEqualTo(401);
        assertThat(chercher(jetonKarim, QUESTION).statut()).isEqualTo(401);
        assertThat(poser(jetonKarim, QUESTION).statut()).isEqualTo(401);
        assertThat(CLAUDE.requetes()).hasSize(1);
    }

    @Test
    @DisplayName("un droit rendu rouvre l'accès aussitôt, sans nouvelle connexion")
    void droitRendu() throws Exception {
        modifierKarim(true);
        assertThat(accesDeKarim()).doesNotContainValue(true);

        modifierKarim(true, "RH");

        assertThat(accesDeKarim()).doesNotContainValue(false);
    }

    @Test
    @DisplayName("la révocation est aussi immédiate quand elle est faite directement en base")
    void aucunCacheDesDroits() throws Exception {
        sql.update("DELETE FROM utilisateur_groupe WHERE utilisateur_id = ? AND groupe_id = ?", karim, groupe("RH"));

        assertThat(accesDeKarim()).doesNotContainValue(true);
    }
}

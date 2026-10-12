package fr.sesame.comptes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sesame.SocleIntegration;
import fr.sesame.securite.ControleurAuthentification;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Gestion des comptes, des groupes et des droits par l'administrateur */
class AdministrationTest extends SocleIntegration {

    private UUID admin;
    private String jetonAdmin;

    @BeforeEach
    void creerAdmin() {
        admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        jetonAdmin = porteur(admin);
    }

    private MockHttpServletRequestBuilder enJson(MockHttpServletRequestBuilder requete, String jeton, Object corps)
            throws Exception {
        return requete.header("Authorization", jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corps));
    }

    private Map<String, Object> creation(String email, Role role, UUID... groupes) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("email", email);
        corps.put("nom", "Nom de test");
        corps.put("motDePasse", MOT_DE_PASSE);
        corps.put("role", role);
        corps.put("groupes", List.of(groupes));
        return corps;
    }

    private Map<String, Object> modification(Role role, boolean actif, UUID... groupes) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("nom", "Nom modifié");
        corps.put("role", role);
        corps.put("actif", actif);
        corps.put("groupes", List.of(groupes));
        return corps;
    }

    // ==================== RÔLES ====================

    @ParameterizedTest(name = "{0} {1} est interdit à un lecteur et à un contributeur")
    @CsvSource({
            "GET, /api/admin/utilisateurs",
            "POST, /api/admin/utilisateurs",
            "PUT, /api/admin/utilisateurs/7f3c1c0e-58a3-4a0e-9d5b-0a4f1c2d3e4f",
            "GET, /api/admin/groupes",
            "POST, /api/admin/groupes",
            "PUT, /api/admin/groupes/7f3c1c0e-58a3-4a0e-9d5b-0a4f1c2d3e4f",
            "DELETE, /api/admin/groupes/7f3c1c0e-58a3-4a0e-9d5b-0a4f1c2d3e4f",
            "GET, /api/admin/journal"
    })
    void routesReserveesAuxAdministrateurs(String methode, String chemin) throws Exception {
        String lecteur = porteur(compte("lea@exemple.test", Role.LECTEUR));
        String contributeur = porteur(compte("karim@exemple.test", Role.CONTRIBUTEUR));

        for (String jeton : List.of(lecteur, contributeur)) {
            mvc.perform(enJson(request(HttpMethod.valueOf(methode), chemin), jeton,
                            creation("pirate@exemple.test", Role.ADMINISTRATEUR)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.erreur").value("Accès refusé"));
        }
        assertThat(sql.queryForObject("SELECT count(*) FROM utilisateur", Integer.class)).isEqualTo(3);
    }

    // ==================== COMPTES ====================

    @Test
    @DisplayName("l'administrateur crée un compte, inscrit d'office dans le groupe Tous")
    void creationCompte() throws Exception {
        UUID rh = groupe("RH");

        mvc.perform(enJson(post("/api/admin/utilisateurs"), jetonAdmin,
                        creation("Nouvelle@Exemple.test", Role.CONTRIBUTEUR, rh)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nouvelle@exemple.test"))
                .andExpect(jsonPath("$.role").value("CONTRIBUTEUR"))
                .andExpect(jsonPath("$.groupes[*].nom").value(contains("RH", "Tous")))
                .andExpect(jsonPath("$.motDePasse").doesNotExist());

        // Le mot de passe est stocké sous forme d'empreinte bcrypt
        String stocke = sql.queryForObject(
                "SELECT mot_de_passe FROM utilisateur WHERE email = 'nouvelle@exemple.test'", String.class);
        assertThat(stocke).startsWith("$2").doesNotContain(MOT_DE_PASSE);
        assertThat(encodeur.matches(MOT_DE_PASSE, stocke)).isTrue();
        assertThat(actionsAudit()).containsExactly("COMPTE_CREE");
    }

    @Test
    @DisplayName("un e-mail déjà pris est refusé en 409, quelle que soit la casse")
    void emailDejaPris() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR);

        mvc.perform(enJson(post("/api/admin/utilisateurs"), jetonAdmin, creation("LEA@exemple.test", Role.LECTEUR)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("une création invalide est refusée en 400 : mot de passe court, e-mail, rôle ou groupe inconnu")
    void creationInvalide() throws Exception {
        Map<String, Object> motDePasseCourt = creation("a@exemple.test", Role.LECTEUR);
        motDePasseCourt.put("motDePasse", "court");
        Map<String, Object> emailInvalide = creation("pas-un-email", Role.LECTEUR);
        Map<String, Object> roleInconnu = creation("b@exemple.test", Role.LECTEUR);
        roleInconnu.put("role", "SUPER_ADMIN");
        Map<String, Object> groupeInconnu = creation("c@exemple.test", Role.LECTEUR, UUID.randomUUID());
        Map<String, Object> motDePasseTropLong = creation("d@exemple.test", Role.LECTEUR);
        motDePasseTropLong.put("motDePasse", "é".repeat(40));

        for (var corps : List.of(motDePasseCourt, emailInvalide, roleInconnu, groupeInconnu, motDePasseTropLong)) {
            mvc.perform(enJson(post("/api/admin/utilisateurs"), jetonAdmin, corps))
                    .andExpect(status().isBadRequest());
        }
        assertThat(sql.queryForObject("SELECT count(*) FROM utilisateur", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("la modification remplace les groupes et consigne les droits avant et après")
    void modificationDesDroits() throws Exception {
        UUID rh = groupe("RH");
        UUID finance = groupe("Finance");
        UUID lea = compte("lea@exemple.test", Role.LECTEUR, "RH");

        mvc.perform(enJson(put("/api/admin/utilisateurs/" + lea), jetonAdmin,
                        modification(Role.CONTRIBUTEUR, true, finance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONTRIBUTEUR"))
                .andExpect(jsonPath("$.groupes[*].nom").value(contains("Finance", "Tous")));

        JsonNode details = json.readTree(sql.queryForObject(
                "SELECT details::text FROM journal_audit WHERE action = 'COMPTE_MODIFIE'", String.class));
        assertThat(details.at("/avant/role").asText()).isEqualTo("LECTEUR");
        assertThat(details.at("/avant/groupes").toString()).isEqualTo("[\"RH\",\"Tous\"]");
        assertThat(details.at("/apres/role").asText()).isEqualTo("CONTRIBUTEUR");
        assertThat(details.at("/apres/groupes").toString()).isEqualTo("[\"Finance\",\"Tous\"]");
        assertThat(sql.queryForObject(
                "SELECT acteur_email FROM journal_audit WHERE action = 'COMPTE_MODIFIE'", String.class))
                .isEqualTo("admin@exemple.test");
        assertThat(rh).isNotNull();
    }

    @Test
    @DisplayName("un rôle retiré prend effet à la requête suivante, avec le même jeton")
    void retraitDeRoleImmediat() throws Exception {
        UUID second = compte("second@exemple.test", Role.ADMINISTRATEUR);
        String jetonSecond = porteur(second);
        mvc.perform(get("/api/admin/utilisateurs").header("Authorization", jetonSecond)).andExpect(status().isOk());

        mvc.perform(enJson(put("/api/admin/utilisateurs/" + second), jetonAdmin, modification(Role.LECTEUR, true)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/utilisateurs").header("Authorization", jetonSecond))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/moi").header("Authorization", jetonSecond))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LECTEUR"));
    }

    @Test
    @DisplayName("un compte désactivé perd l'accès à la requête suivante et sa session est révoquée")
    void desactivationImmediate() throws Exception {
        UUID lea = compte("lea@exemple.test", Role.LECTEUR);
        String jetonLea = porteur(lea);
        Cookie session = mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "lea@exemple.test", "motDePasse", MOT_DE_PASSE))))
                .andReturn().getResponse().getCookie(ControleurAuthentification.COOKIE);

        mvc.perform(enJson(put("/api/admin/utilisateurs/" + lea), jetonAdmin, modification(Role.LECTEUR, false)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/moi").header("Authorization", jetonLea)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/renouvellement").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(sql.queryForObject("SELECT bool_and(revoque) FROM jeton_renouvellement", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("un nouveau mot de passe remplace l'ancien et ferme les sessions ouvertes")
    void changementDeMotDePasse() throws Exception {
        UUID lea = compte("lea@exemple.test", Role.LECTEUR);
        Map<String, Object> corps = modification(Role.LECTEUR, true);
        corps.put("motDePasse", "un-nouveau-mot-de-passe");

        mvc.perform(enJson(put("/api/admin/utilisateurs/" + lea), jetonAdmin, corps)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "lea@exemple.test", "motDePasse", MOT_DE_PASSE))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "lea@exemple.test", "motDePasse", "un-nouveau-mot-de-passe"))))
                .andExpect(status().isOk());
        // Le journal dit que le mot de passe a changé, sans le contenir
        String journal = sql.queryForObject(
                "SELECT details::text FROM journal_audit WHERE action = 'COMPTE_MODIFIE'", String.class);
        assertThat(journal).contains("\"motDePasseChange\": true").doesNotContain("un-nouveau-mot-de-passe");
    }

    @ParameterizedTest(name = "un administrateur ne peut pas se rétrograder lui-même en {0}")
    @EnumSource(value = Role.class, names = {"LECTEUR", "CONTRIBUTEUR"})
    void pasDAutoRetrogradation(Role role) throws Exception {
        mvc.perform(enJson(put("/api/admin/utilisateurs/" + admin), jetonAdmin, modification(role, true)))
                .andExpect(status().isConflict());
        mvc.perform(enJson(put("/api/admin/utilisateurs/" + admin), jetonAdmin,
                        modification(Role.ADMINISTRATEUR, false)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/admin/utilisateurs").header("Authorization", jetonAdmin)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un compte inconnu répond 404 et un identifiant mal formé 400")
    void compteInconnu() throws Exception {
        mvc.perform(enJson(put("/api/admin/utilisateurs/" + UUID.randomUUID()), jetonAdmin,
                        modification(Role.LECTEUR, true)))
                .andExpect(status().isNotFound());
        mvc.perform(enJson(put("/api/admin/utilisateurs/pas-un-uuid"), jetonAdmin, modification(Role.LECTEUR, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la liste des comptes n'expose jamais les empreintes de mots de passe")
    void listeSansMotsDePasse() throws Exception {
        compte("lea@exemple.test", Role.LECTEUR, "RH");

        String corps = mvc.perform(get("/api/admin/utilisateurs").header("Authorization", jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email").value(contains("admin@exemple.test", "lea@exemple.test")))
                .andReturn().getResponse().getContentAsString();

        assertThat(corps).doesNotContain("$2").doesNotContain("motDePasse");
    }

    // ==================== GROUPES ====================

    @Test
    @DisplayName("l'administrateur crée, renomme et liste les groupes")
    void cycleDeVieDUnGroupe() throws Exception {
        String id = corps(mvc.perform(enJson(post("/api/admin/groupes"), jetonAdmin, Map.of("nom", "Juridique")))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        mvc.perform(enJson(put("/api/admin/groupes/" + id), jetonAdmin, Map.of("nom", "Affaires juridiques")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/groupes").header("Authorization", jetonAdmin))
                .andExpect(jsonPath("$[*].nom").value(containsInAnyOrder("Affaires juridiques", "Tous")));
        assertThat(actionsAudit()).containsExactly("GROUPE_CREE", "GROUPE_RENOMME");
    }

    @Test
    @DisplayName("deux groupes ne peuvent pas porter le même nom")
    void nomDeGroupeUnique() throws Exception {
        groupe("RH");

        mvc.perform(enJson(post("/api/admin/groupes"), jetonAdmin, Map.of("nom", "rh")))
                .andExpect(status().isConflict());
        mvc.perform(enJson(post("/api/admin/groupes"), jetonAdmin, Map.of("nom", "  ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("supprimer un groupe retire l'appartenance de ses membres")
    void suppressionDeGroupe() throws Exception {
        UUID rh = groupe("RH");
        UUID lea = compte("lea@exemple.test", Role.LECTEUR, "RH");

        mvc.perform(delete("/api/admin/groupes/" + rh).header("Authorization", jetonAdmin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/moi").header("Authorization", porteur(lea)))
                .andExpect(jsonPath("$.groupes[*].nom").value(contains("Tous")));
        assertThat(actionsAudit()).containsExactly("GROUPE_SUPPRIME");
    }

    @Test
    @DisplayName("le groupe Tous ne peut être ni renommé ni supprimé")
    void groupeTousProtege() throws Exception {
        UUID tous = groupe("Tous");

        mvc.perform(delete("/api/admin/groupes/" + tous).header("Authorization", jetonAdmin))
                .andExpect(status().isConflict());
        mvc.perform(enJson(put("/api/admin/groupes/" + tous), jetonAdmin, Map.of("nom", "Personne")))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/groupes/" + UUID.randomUUID()).header("Authorization", jetonAdmin))
                .andExpect(status().isNotFound());
    }

    // ==================== JOURNAL ====================

    @Test
    @DisplayName("le journal d'audit rend les événements du plus récent au plus ancien")
    void journal() throws Exception {
        mvc.perform(enJson(post("/api/admin/groupes"), jetonAdmin, Map.of("nom", "RH")));
        mvc.perform(enJson(post("/api/admin/groupes"), jetonAdmin, Map.of("nom", "Finance")));

        mvc.perform(get("/api/admin/journal?taille=1").header("Authorization", jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.lignes[0].action").value("GROUPE_CREE"))
                .andExpect(jsonPath("$.lignes[0].acteurEmail").value("admin@exemple.test"))
                .andExpect(jsonPath("$.lignes[0].details.nom").value("Finance"));
    }
}

package fr.sesame.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.sesame.SocleIntegration;
import fr.sesame.comptes.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Jeton absent, expiré ou falsifié : aucune route protégée ne doit répondre */
class AccesProtegeTest extends SocleIntegration {

    private static final Set<String> ROUTES_PUBLIQUES =
            Set.of("/api/auth/connexion", "/api/auth/renouvellement", "/api/auth/deconnexion");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping routes;

    private record Route(HttpMethod methode, String chemin) {
    }

    /** Toutes les routes déclarées par l'application : une route ajoutée demain est testée d'office */
    private List<Route> routesProtegees() {
        List<Route> resultat = new ArrayList<>();
        routes.getHandlerMethods().forEach((info, methode) -> {
            if (!methode.getBeanType().getPackageName().startsWith("fr.sesame")) {
                return;
            }
            for (String chemin : info.getPatternValues()) {
                if (ROUTES_PUBLIQUES.contains(chemin)) {
                    continue;
                }
                String concret = chemin.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                info.getMethodsCondition().getMethods()
                        .forEach(m -> resultat.add(new Route(m.asHttpMethod(), concret)));
            }
        });
        return resultat;
    }

    private void attendreRefus(String entete) throws Exception {
        for (Route route : routesProtegees()) {
            var requete = request(route.methode(), route.chemin());
            if (entete != null) {
                requete.header("Authorization", entete);
            }
            mvc.perform(requete)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"erreur\":\"Non authentifié\"}"));
        }
    }

    @Test
    @DisplayName("la liste des routes protégées couvre bien l'application")
    void routesRecensees() {
        assertThat(routesProtegees()).extracting(Route::chemin)
                .contains("/api/moi", "/api/admin/utilisateurs", "/api/admin/groupes", "/api/admin/journal");
    }

    @Test
    @DisplayName("sans jeton, chaque route protégée répond 401")
    void jetonAbsent() throws Exception {
        attendreRefus(null);
    }

    @Test
    @DisplayName("un jeton expiré est refusé")
    void jetonExpire() throws Exception {
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        String expire = serviceJeton.emettre(admin, Instant.now().minus(11, ChronoUnit.MINUTES));

        attendreRefus("Bearer " + expire);
    }

    @Test
    @DisplayName("un jeton signé avec une autre clé est refusé")
    void signatureFalsifiee() throws Exception {
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        String forge = Jwts.builder()
                .subject(admin.toString())
                .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor("une-autre-cle-de-signature-de-32-octets!".getBytes(StandardCharsets.UTF_8)))
                .compact();

        attendreRefus("Bearer " + forge);
    }

    @Test
    @DisplayName("un jeton non signé (alg none) est refusé")
    void jetonNonSigne() throws Exception {
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        long expiration = Instant.now().plus(10, ChronoUnit.MINUTES).getEpochSecond();
        String nonSigne = base64("{\"alg\":\"none\"}") + "."
                + base64("{\"sub\":\"" + admin + "\",\"exp\":" + expiration + "}") + ".";

        attendreRefus("Bearer " + nonSigne);
    }

    @Test
    @DisplayName("un jeton dont le contenu a été modifié après signature est refusé")
    void contenuModifie() throws Exception {
        UUID lecteur = compte("lea@exemple.test", Role.LECTEUR);
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        String[] parties = serviceJeton.emettre(lecteur).split("\\.");
        long expiration = Instant.now().plus(10, ChronoUnit.MINUTES).getEpochSecond();
        // Le lecteur remplace son identifiant par celui de l'administrateur, en gardant sa signature
        String modifie = parties[0] + "." + base64("{\"sub\":\"" + admin + "\",\"exp\":" + expiration + "}")
                + "." + parties[2];

        attendreRefus("Bearer " + modifie);
    }

    @Test
    @DisplayName("un en-tête illisible ou d'un autre type est refusé")
    void enteteInvalide() throws Exception {
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);

        attendreRefus("Bearer n-importe-quoi");
        attendreRefus("Bearer ");
        attendreRefus("Basic " + base64("admin@exemple.test:" + MOT_DE_PASSE));
        attendreRefus(serviceJeton.emettre(admin));
    }

    @Test
    @DisplayName("le jeton encore valide d'un compte supprimé ou désactivé est refusé")
    void compteDisparu() throws Exception {
        UUID supprime = compte("parti@exemple.test", Role.ADMINISTRATEUR);
        UUID desactive = compte("ancien@exemple.test", Role.ADMINISTRATEUR);
        String jetonSupprime = porteur(supprime);
        String jetonDesactive = porteur(desactive);
        mvc.perform(get("/api/moi").header("Authorization", jetonSupprime)).andExpect(status().isOk());

        sql.update("DELETE FROM utilisateur WHERE id = ?", supprime);
        sql.update("UPDATE utilisateur SET actif = false WHERE id = ?", desactive);

        attendreRefus(jetonSupprime);
        attendreRefus(jetonDesactive);
    }

    @Test
    @DisplayName("une route inconnue répond 401 sans jeton et 404 avec : elle ne renseigne pas un anonyme")
    void routeInconnue() throws Exception {
        UUID lecteur = compte("lea@exemple.test", Role.LECTEUR);

        mvc.perform(get("/api/inexistante")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inexistante").header("Authorization", porteur(lecteur)))
                .andExpect(status().isNotFound());
    }

    private static String base64(String texte) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texte.getBytes(StandardCharsets.UTF_8));
    }
}

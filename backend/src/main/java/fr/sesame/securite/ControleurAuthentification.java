package fr.sesame.securite;

import fr.sesame.commun.ErreurMetier;
import fr.sesame.comptes.Profil;
import fr.sesame.comptes.ServiceComptes;
import fr.sesame.securite.ServiceAuthentification.Session;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ControleurAuthentification - Connexion, renouvellement et déconnexion
 *
 * Le jeton d'accès (court) est rendu dans le corps et gardé en mémoire par le
 * navigateur. Le jeton de renouvellement voyage dans un cookie HttpOnly et
 * SameSite=Strict, limité à /api/auth : le JavaScript de la page ne peut pas le lire.
 */
@RestController
@RequestMapping("/api/auth")
public class ControleurAuthentification {

    public static final String COOKIE = "sesame_renouvellement";
    private static final String CHEMIN_COOKIE = "/api/auth";

    public record DemandeConnexion(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String motDePasse) {
    }

    public record ReponseSession(String jetonAcces, long expireDans, Profil utilisateur) {
    }

    private final ServiceAuthentification service;
    private final LimiteurDebit limiteParEmail;
    private final LimiteurDebit limiteParAdresse;
    private final ProprietesSecurite proprietes;

    public ControleurAuthentification(ServiceAuthentification service,
            @Qualifier("limiteConnexionParEmail") LimiteurDebit limiteParEmail,
            @Qualifier("limiteConnexionParAdresse") LimiteurDebit limiteParAdresse,
            ProprietesSecurite proprietes) {
        this.service = service;
        this.limiteParEmail = limiteParEmail;
        this.limiteParAdresse = limiteParAdresse;
        this.proprietes = proprietes;
    }

    @PostMapping("/connexion")
    public ResponseEntity<ReponseSession> connecter(@Valid @RequestBody DemandeConnexion demande,
            HttpServletRequest requete) {
        String email = ServiceComptes.normaliserEmail(demande.email());
        // La limite s'applique avant toute vérification du mot de passe
        limiteParAdresse.consommer(requete.getRemoteAddr()).ifPresent(this::refuser);
        limiteParEmail.consommer(email).ifPresent(this::refuser);

        Session session = service.connecter(email, demande.motDePasse())
                .orElseThrow(ErreurMetier::identifiantsInvalides);
        limiteParEmail.oublier(email);
        return repondre(session);
    }

    @PostMapping("/renouvellement")
    public ResponseEntity<ReponseSession> renouveler(@CookieValue(name = COOKIE, required = false) String jeton) {
        if (jeton == null) {
            throw ErreurMetier.nonAuthentifie();
        }
        return repondre(service.renouveler(jeton).orElseThrow(ErreurMetier::nonAuthentifie));
    }

    @PostMapping("/deconnexion")
    public ResponseEntity<Void> deconnecter(@CookieValue(name = COOKIE, required = false) String jeton) {
        if (jeton != null) {
            service.deconnecter(jeton);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private void refuser(Duration attente) {
        throw ErreurMetier.tropDeRequetes(attente);
    }

    private ResponseEntity<ReponseSession> repondre(Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookie(session.jetonRenouvellement(), proprietes.dureeRenouvellement()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ReponseSession(session.jetonAcces(), session.expireDans(), session.utilisateur()));
    }

    private ResponseCookie cookie(String valeur, Duration duree) {
        return ResponseCookie.from(COOKIE, valeur)
                .httpOnly(true)
                .secure(proprietes.cookieSecurise())
                .sameSite("Strict")
                .path(CHEMIN_COOKIE)
                .maxAge(duree)
                .build();
    }
}

package fr.sesame.commun;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Refus prévu par l'API, traduit en réponse HTTP par {@link GestionErreurs} */
public class ErreurMetier extends RuntimeException {

    private final HttpStatus statut;
    private final Duration attente;

    private ErreurMetier(HttpStatus statut, String message, Duration attente) {
        super(message);
        this.statut = statut;
        this.attente = attente;
    }

    public static ErreurMetier requeteInvalide(String message) {
        return new ErreurMetier(HttpStatus.BAD_REQUEST, message, null);
    }

    public static ErreurMetier nonAuthentifie() {
        return new ErreurMetier(HttpStatus.UNAUTHORIZED, "Non authentifié", null);
    }

    public static ErreurMetier identifiantsInvalides() {
        return new ErreurMetier(HttpStatus.UNAUTHORIZED, "Identifiants invalides", null);
    }

    public static ErreurMetier introuvable() {
        return new ErreurMetier(HttpStatus.NOT_FOUND, "Introuvable", null);
    }

    public static ErreurMetier conflit(String message) {
        return new ErreurMetier(HttpStatus.CONFLICT, message, null);
    }

    public static ErreurMetier tropVolumineux() {
        return new ErreurMetier(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux", null);
    }

    public static ErreurMetier indisponible(String message) {
        return new ErreurMetier(HttpStatus.SERVICE_UNAVAILABLE, message, null);
    }

    public static ErreurMetier tropDeRequetes(Duration attente) {
        return new ErreurMetier(HttpStatus.TOO_MANY_REQUESTS, "Trop de requêtes, réessayez plus tard", attente);
    }

    public HttpStatus statut() {
        return statut;
    }

    /** Délai avant un nouvel essai, renseigné pour les refus de débit */
    public Duration attente() {
        return attente;
    }
}

package fr.sesame.securite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LimiteurDebit - Limite de débit à fenêtre fixe, en mémoire
 *
 * Chaque clé (e-mail, adresse IP, compte) a droit à un nombre d'essais par fenêtre.
 * L'état vit dans la mémoire du processus : il convient à une instance unique.
 */
public class LimiteurDebit {

    // Au-delà de ce nombre de clés, les fenêtres expirées sont purgées
    private static final int SEUIL_PURGE = 10_000;

    private record Fenetre(Instant debut, int essais) {
    }

    private final int essaisMax;
    private final Duration duree;
    private final Clock horloge;
    private final ConcurrentHashMap<String, Fenetre> fenetres = new ConcurrentHashMap<>();

    public LimiteurDebit(int essaisMax, Duration duree, Clock horloge) {
        this.essaisMax = essaisMax;
        this.duree = duree;
        this.horloge = horloge;
    }

    /**
     * Compte un essai pour la clé.
     *
     * @return vide si l'essai est autorisé, sinon le délai avant la prochaine fenêtre
     */
    public Optional<Duration> consommer(String cle) {
        Instant maintenant = horloge.instant();
        if (fenetres.size() > SEUIL_PURGE) {
            fenetres.values().removeIf(fenetre -> expiree(fenetre, maintenant));
        }
        Fenetre fenetre = fenetres.compute(cle, (k, actuelle) ->
                actuelle == null || expiree(actuelle, maintenant)
                        ? new Fenetre(maintenant, 1)
                        : new Fenetre(actuelle.debut(), actuelle.essais() + 1));
        if (fenetre.essais() <= essaisMax) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(maintenant, fenetre.debut().plus(duree)));
    }

    /** Remet le compteur de la clé à zéro (après une connexion réussie, par exemple) */
    public void oublier(String cle) {
        fenetres.remove(cle);
    }

    public void reinitialiser() {
        fenetres.clear();
    }

    private boolean expiree(Fenetre fenetre, Instant maintenant) {
        return !maintenant.isBefore(fenetre.debut().plus(duree));
    }
}

package fr.sesame.securite;

import fr.sesame.audit.ActionAudit;
import fr.sesame.audit.ServiceAudit;
import fr.sesame.comptes.DepotUtilisateurs;
import fr.sesame.comptes.Profil;
import fr.sesame.comptes.Utilisateur;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ServiceAuthentification - Connexion, renouvellement et déconnexion
 *
 * Les échecs sont rendus par un résultat vide plutôt que par une exception :
 * la transaction est ainsi validée, et l'audit ou la révocation qui accompagnent
 * un refus restent enregistrés.
 */
@Service
public class ServiceAuthentification {

    /** Session ouverte : le jeton de renouvellement part dans un cookie, jamais dans le corps */
    public record Session(String jetonAcces, long expireDans, Profil utilisateur, String jetonRenouvellement) {
    }

    private final DepotUtilisateurs utilisateurs;
    private final DepotJetonsRenouvellement jetons;
    private final ServiceJeton serviceJeton;
    private final ServiceAudit audit;
    private final PasswordEncoder encodeur;
    private final ProprietesSecurite proprietes;
    private final Clock horloge;
    private final SecureRandom alea = new SecureRandom();
    // Comparée quand l'e-mail est inconnu, pour que la réponse prenne le même temps
    private final String empreinteFactice;

    public ServiceAuthentification(DepotUtilisateurs utilisateurs, DepotJetonsRenouvellement jetons,
            ServiceJeton serviceJeton, ServiceAudit audit, PasswordEncoder encodeur,
            ProprietesSecurite proprietes, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.jetons = jetons;
        this.serviceJeton = serviceJeton;
        this.audit = audit;
        this.encodeur = encodeur;
        this.proprietes = proprietes;
        this.horloge = horloge;
        this.empreinteFactice = encodeur.encode(UUID.randomUUID().toString());
    }

    /** E-mail inconnu, mot de passe faux et compte désactivé donnent le même refus */
    @Transactional
    public Optional<Session> connecter(String email, String motDePasse) {
        Optional<Utilisateur> compte = utilisateurs.findByEmail(email);
        boolean motDePasseJuste = encodeur.matches(motDePasse,
                compte.map(Utilisateur::getMotDePasse).orElse(empreinteFactice));
        if (compte.isEmpty() || !motDePasseJuste || !compte.get().isActif()) {
            audit.consigner(compte.map(Utilisateur::getId).orElse(null), email,
                    ActionAudit.CONNEXION_ECHOUEE, null, null, Map.of());
            return Optional.empty();
        }
        Utilisateur utilisateur = compte.get();
        audit.consigner(utilisateur.getId(), utilisateur.getEmail(),
                ActionAudit.CONNEXION_REUSSIE, null, null, Map.of());
        return Optional.of(ouvrir(utilisateur, UUID.randomUUID()));
    }

    /**
     * Échange un jeton de renouvellement contre une nouvelle session (rotation).
     * Un jeton déjà utilisé a peut-être été volé : toute sa famille est révoquée.
     */
    @Transactional
    public Optional<Session> renouveler(String valeur) {
        Optional<JetonRenouvellement> trouve = jetons.findByEmpreinte(empreinte(valeur));
        if (trouve.isEmpty()) {
            return Optional.empty();
        }
        JetonRenouvellement jeton = trouve.get();
        Instant maintenant = horloge.instant();
        if (jetons.revoquerSiValide(jeton.getId()) == 0) {
            jetons.revoquerFamille(jeton.getFamille());
            return Optional.empty();
        }
        if (!maintenant.isBefore(jeton.getExpireLe())) {
            return Optional.empty();
        }
        return utilisateurs.findById(jeton.getUtilisateurId())
                .filter(Utilisateur::isActif)
                .map(utilisateur -> ouvrir(utilisateur, jeton.getFamille()));
    }

    @Transactional
    public void deconnecter(String valeur) {
        jetons.findByEmpreinte(empreinte(valeur))
                .ifPresent(jeton -> jetons.revoquerFamille(jeton.getFamille()));
    }

    private Session ouvrir(Utilisateur utilisateur, UUID famille) {
        byte[] octets = new byte[32];
        alea.nextBytes(octets);
        String valeur = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        Instant maintenant = horloge.instant();
        jetons.save(new JetonRenouvellement(utilisateur.getId(), empreinte(valeur), famille,
                maintenant, maintenant.plus(proprietes.dureeRenouvellement())));
        return new Session(serviceJeton.emettre(utilisateur.getId()), serviceJeton.dureeEnSecondes(),
                Profil.de(utilisateur), valeur);
    }

    static String empreinte(String valeur) {
        try {
            byte[] condense = MessageDigest.getInstance("SHA-256").digest(valeur.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(condense);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

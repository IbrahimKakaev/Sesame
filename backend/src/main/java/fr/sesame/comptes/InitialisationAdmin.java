package fr.sesame.comptes;

import fr.sesame.comptes.ServiceComptes.CreationCompte;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * InitialisationAdmin - Crée le premier administrateur au démarrage
 *
 * Sans lui, personne ne pourrait créer les autres comptes. Ses identifiants viennent
 * des variables SESAME_ADMIN_EMAIL et SESAME_ADMIN_MOT_DE_PASSE, jamais du dépôt.
 */
@Component
public class InitialisationAdmin implements ApplicationRunner {

    private static final Logger journal = LoggerFactory.getLogger(InitialisationAdmin.class);

    private final ServiceComptes service;
    private final DepotUtilisateurs utilisateurs;
    private final String email;
    private final String motDePasse;

    public InitialisationAdmin(ServiceComptes service, DepotUtilisateurs utilisateurs,
            @Value("${sesame.admin-initial.email:}") String email,
            @Value("${sesame.admin-initial.mot-de-passe:}") String motDePasse) {
        this.service = service;
        this.utilisateurs = utilisateurs;
        this.email = email;
        this.motDePasse = motDePasse;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (email.isBlank()) {
            return;
        }
        String normalise = ServiceComptes.normaliserEmail(email);
        if (utilisateurs.existsByEmail(normalise)) {
            return;
        }
        if (motDePasse.length() < ServiceComptes.LONGUEUR_MIN_MOT_DE_PASSE) {
            throw new IllegalStateException("SESAME_ADMIN_MOT_DE_PASSE doit contenir au moins "
                    + ServiceComptes.LONGUEUR_MIN_MOT_DE_PASSE + " caractères");
        }
        service.creer(null, new CreationCompte(normalise, "Administrateur", motDePasse, Role.ADMINISTRATEUR, Set.of()));
        journal.info("Compte administrateur initial créé : {}", normalise);
    }
}

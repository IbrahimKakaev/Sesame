package fr.sesame.comptes;

import fr.sesame.securite.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profil du compte connecté, avec son rôle et ses groupes à l'instant de la requête */
@RestController
public class ControleurProfil {

    private final ServiceComptes service;

    public ControleurProfil(ServiceComptes service) {
        this.service = service;
    }

    @GetMapping("/api/moi")
    public Profil moi(@AuthenticationPrincipal UtilisateurConnecte auteur) {
        return service.profil(auteur.id());
    }
}

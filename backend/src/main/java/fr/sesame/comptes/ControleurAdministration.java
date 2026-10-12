package fr.sesame.comptes;

import fr.sesame.comptes.Profil.GroupeResume;
import fr.sesame.comptes.ServiceComptes.CreationCompte;
import fr.sesame.comptes.ServiceComptes.ModificationCompte;
import fr.sesame.comptes.ServiceComptes.NomGroupe;
import fr.sesame.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ControleurAdministration - Comptes et groupes
 * Toutes les routes /api/admin sont réservées aux administrateurs (ConfigurationSecurite).
 */
@RestController
@RequestMapping("/api/admin")
public class ControleurAdministration {

    private final ServiceComptes service;

    public ControleurAdministration(ServiceComptes service) {
        this.service = service;
    }

    @GetMapping("/utilisateurs")
    public List<Profil> listerUtilisateurs() {
        return service.lister();
    }

    @PostMapping("/utilisateurs")
    @ResponseStatus(HttpStatus.CREATED)
    public Profil creerUtilisateur(@AuthenticationPrincipal UtilisateurConnecte acteur,
            @Valid @RequestBody CreationCompte demande) {
        return service.creer(acteur, demande);
    }

    @PutMapping("/utilisateurs/{id}")
    public Profil modifierUtilisateur(@AuthenticationPrincipal UtilisateurConnecte acteur,
            @PathVariable UUID id, @Valid @RequestBody ModificationCompte demande) {
        return service.modifier(acteur, id, demande);
    }

    @GetMapping("/groupes")
    public List<GroupeResume> listerGroupes() {
        return service.listerGroupes();
    }

    @PostMapping("/groupes")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupeResume creerGroupe(@AuthenticationPrincipal UtilisateurConnecte acteur,
            @Valid @RequestBody NomGroupe demande) {
        return service.creerGroupe(acteur, demande);
    }

    @PutMapping("/groupes/{id}")
    public GroupeResume renommerGroupe(@AuthenticationPrincipal UtilisateurConnecte acteur,
            @PathVariable UUID id, @Valid @RequestBody NomGroupe demande) {
        return service.renommerGroupe(acteur, id, demande);
    }

    @DeleteMapping("/groupes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerGroupe(@AuthenticationPrincipal UtilisateurConnecte acteur, @PathVariable UUID id) {
        service.supprimerGroupe(acteur, id);
    }
}

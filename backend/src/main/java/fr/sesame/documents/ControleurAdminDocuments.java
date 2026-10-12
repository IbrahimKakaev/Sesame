package fr.sesame.documents;

import fr.sesame.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ControleurAdminDocuments - Droits des documents
 *
 * Administrer n'est pas lire : l'administrateur voit les métadonnées de tous les
 * documents pour en gérer les groupes, mais leur contenu, leurs extraits et les
 * réponses à ses questions passent par ses propres groupes, comme pour tout le monde.
 */
@RestController
@RequestMapping("/api/admin/documents")
public class ControleurAdminDocuments {

    public record GroupesDocument(@NotNull Set<UUID> groupes) {
    }

    private final ServiceDepot depot;

    public ControleurAdminDocuments(ServiceDepot depot) {
        this.depot = depot;
    }

    @GetMapping
    public List<DocumentResume> lister() {
        return depot.tous();
    }

    @PutMapping("/{id}/groupes")
    public DocumentResume changerGroupes(@AuthenticationPrincipal UtilisateurConnecte acteur, @PathVariable UUID id,
            @Valid @RequestBody GroupesDocument demande) {
        return depot.changerGroupes(acteur, id, demande.groupes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal UtilisateurConnecte acteur, @PathVariable UUID id) {
        depot.supprimer(acteur, id);
    }
}

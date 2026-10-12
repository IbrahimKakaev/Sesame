package fr.sesame.documents;

import fr.sesame.audit.ActionAudit;
import fr.sesame.audit.ServiceAudit;
import fr.sesame.commun.ErreurMetier;
import fr.sesame.comptes.Role;
import fr.sesame.securite.UtilisateurConnecte;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ServiceDepot - Dépôt d'un document et gestion de ses droits
 *
 * Le dépôt contrôle le fichier (taille, signature PDF), assainit son nom, enregistre
 * le document « en attente » puis laisse l'indexation se faire en arrière-plan.
 */
@Service
public class ServiceDepot {

    /** Publié à l'enregistrement d'un document ; l'indexation démarre une fois la transaction validée */
    public record DocumentDepose(UUID id) {
    }

    // Tout PDF commence par ces octets, quelle que soit son extension
    private static final byte[] SIGNATURE_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final DepotDocuments documents;
    private final ServiceAudit audit;
    private final ApplicationEventPublisher evenements;
    private final ProprietesDocuments proprietes;

    public ServiceDepot(DepotDocuments documents, ServiceAudit audit, ApplicationEventPublisher evenements,
            ProprietesDocuments proprietes) {
        this.documents = documents;
        this.audit = audit;
        this.evenements = evenements;
        this.proprietes = proprietes;
    }

    /**
     * @param acteur auteur du dépôt, ou null pour le chargement des données de démonstration
     * @param nomEnvoye nom annoncé par le navigateur : jamais utilisé tel quel
     */
    @Transactional
    public DocumentResume deposer(UtilisateurConnecte acteur, byte[] contenu, String nomEnvoye, String titreEnvoye,
            Collection<UUID> groupesDemandes) {
        if (contenu.length > proprietes.tailleMax().toBytes()) {
            throw ErreurMetier.tropVolumineux();
        }
        // Le type est vérifié sur le contenu : l'extension et le type MIME annoncés ne prouvent rien
        if (!commencePar(contenu, SIGNATURE_PDF)) {
            throw ErreurMetier.requeteInvalide("Seuls les fichiers PDF sont acceptés");
        }
        Set<UUID> groupes = groupesAutorises(acteur, groupesDemandes);

        UUID id = UUID.randomUUID();
        String nom = NomsDeFichiers.assainir(nomEnvoye);
        documents.creer(id, NomsDeFichiers.titre(titreEnvoye, nom), nom, contenu,
                acteur == null ? null : acteur.id(), groupes);
        DocumentResume document = documents.parId(id).orElseThrow();

        audit.consigner(acteur == null ? null : acteur.id(), acteur == null ? null : acteur.email(),
                ActionAudit.DOCUMENT_DEPOSE, "document", id,
                Map.of("titre", document.titre(), "groupes", document.groupes()));
        evenements.publishEvent(new DocumentDepose(id));
        return document;
    }

    /** Remplace les groupes autorisés d'un document : l'effet est immédiat */
    @Transactional
    public DocumentResume changerGroupes(UtilisateurConnecte acteur, UUID id, Collection<UUID> groupesDemandes) {
        DocumentResume avant = documents.parId(id).orElseThrow(ErreurMetier::introuvable);
        Set<UUID> groupes = new HashSet<>(groupesDemandes);
        if (documents.nombreDeGroupesExistants(groupes) != groupes.size()) {
            throw ErreurMetier.requeteInvalide("Groupe inconnu");
        }
        documents.remplacerGroupes(id, groupes);
        DocumentResume apres = documents.parId(id).orElseThrow();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("titre", avant.titre());
        details.put("avant", avant.groupes());
        details.put("apres", apres.groupes());
        audit.consigner(acteur.id(), acteur.email(), ActionAudit.DROITS_DOCUMENT_MODIFIES, "document", id, details);
        return apres;
    }

    @Transactional
    public void supprimer(UtilisateurConnecte acteur, UUID id) {
        DocumentResume document = documents.parId(id).orElseThrow(ErreurMetier::introuvable);
        documents.supprimer(id);
        audit.consigner(acteur.id(), acteur.email(), ActionAudit.DOCUMENT_SUPPRIME, "document", id,
                Map.of("titre", document.titre()));
    }

    /**
     * Un contributeur ne dépose que dans ses propres groupes ; un administrateur, dans
     * n'importe quel groupe existant. Groupe inconnu et groupe d'un autre service
     * donnent le même refus.
     */
    private Set<UUID> groupesAutorises(UtilisateurConnecte acteur, Collection<UUID> demandes) {
        Set<UUID> groupes = demandes == null ? Set.of() : new HashSet<>(demandes);
        if (groupes.isEmpty()) {
            throw ErreurMetier.requeteInvalide("Au moins un groupe est requis");
        }
        boolean autorise = acteur == null || acteur.role() == Role.ADMINISTRATEUR
                ? documents.nombreDeGroupesExistants(groupes) == groupes.size()
                : new HashSet<>(documents.groupesDe(acteur.id())).containsAll(groupes);
        if (!autorise) {
            throw ErreurMetier.requeteInvalide("Groupe non autorisé");
        }
        return groupes;
    }

    private static boolean commencePar(byte[] contenu, byte[] signature) {
        if (contenu.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (contenu[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    public List<DocumentResume> tous() {
        return documents.tous();
    }
}

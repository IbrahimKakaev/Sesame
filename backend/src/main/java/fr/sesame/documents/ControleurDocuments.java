package fr.sesame.documents;

import fr.sesame.commun.ErreurMetier;
import fr.sesame.documents.DepotDocuments.Fichier;
import fr.sesame.securite.UtilisateurConnecte;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ControleurDocuments - Dépôt et consultation des documents
 *
 * Un document que l'utilisateur ne peut pas voir répond 404, exactement comme un
 * document qui n'existe pas : la réponse ne trahit pas son existence.
 */
@RestController
@RequestMapping("/api/documents")
public class ControleurDocuments {

    private final DepotDocuments documents;
    private final ServiceDepot depot;

    public ControleurDocuments(DepotDocuments documents, ServiceDepot depot) {
        this.documents = documents;
        this.depot = depot;
    }

    /** Dépôt d'un PDF, réservé aux contributeurs (ConfigurationSecurite) */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResume deposer(@AuthenticationPrincipal UtilisateurConnecte auteur,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam(value = "titre", required = false) String titre,
            @RequestParam("groupes") List<UUID> groupes) throws IOException {
        return depot.deposer(auteur, fichier.getBytes(), fichier.getOriginalFilename(), titre, groupes);
    }

    @GetMapping
    public List<DocumentResume> lister(@AuthenticationPrincipal UtilisateurConnecte auteur) {
        return documents.visiblesPar(auteur.id());
    }

    @GetMapping("/{id}")
    public DocumentResume detail(@AuthenticationPrincipal UtilisateurConnecte auteur, @PathVariable UUID id) {
        return documents.visible(id, auteur.id()).orElseThrow(ErreurMetier::introuvable);
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<byte[]> fichier(@AuthenticationPrincipal UtilisateurConnecte auteur, @PathVariable UUID id) {
        Fichier fichier = documents.fichierVisible(id, auteur.id()).orElseThrow(ErreurMetier::introuvable);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // Téléchargement forcé et page sans droits : le PDF n'est jamais interprété dans l'origine de l'application
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fichier.nom(), StandardCharsets.UTF_8).build().toString())
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(fichier.contenu());
    }
}

package fr.sesame.recherche;

import fr.sesame.audit.ActionAudit;
import fr.sesame.audit.ServiceAudit;
import fr.sesame.commun.ErreurMetier;
import fr.sesame.recherche.ServiceRecherche.PassageTrouve;
import fr.sesame.securite.LimiteurDebit;
import fr.sesame.securite.UtilisateurConnecte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ControleurRecherche - Recherche de passages et lecture d'un extrait
 *
 * Les résultats ne viennent que des documents visibles par l'utilisateur. Rien
 * dans la réponse (nombre de résultats, scores, message) ne dépend des autres.
 */
@RestController
public class ControleurRecherche {

    private static final int LIMITE_PAR_DEFAUT = 5;

    public record DemandeRecherche(@NotBlank @Size(max = 500) String question, @Min(1) @Max(10) Integer limite) {
    }

    public record Extrait(UUID passageId, UUID documentId, String titre, int page, String extrait) {

        static Extrait de(PassageTrouve passage) {
            return new Extrait(passage.id(), passage.documentId(), passage.titre(), passage.page(), passage.contenu());
        }
    }

    public record Resultats(List<Extrait> resultats) {
    }

    private final ServiceRecherche recherche;
    private final ServiceAudit audit;

    private final LimiteurDebit limite;

    public ControleurRecherche(ServiceRecherche recherche, ServiceAudit audit,
            @Qualifier("limiteQuestions") LimiteurDebit limite) {
        this.recherche = recherche;
        this.audit = audit;
        this.limite = limite;
    }

    @PostMapping("/api/recherche")
    public Resultats chercher(@AuthenticationPrincipal UtilisateurConnecte auteur,
            @Valid @RequestBody DemandeRecherche demande) {
        limite.consommer(auteur.id().toString()).ifPresent(attente -> {
            throw ErreurMetier.tropDeRequetes(attente);
        });
        String question = ServiceRecherche.nettoyer(demande.question());
        if (question.isEmpty()) {
            throw ErreurMetier.requeteInvalide("Question vide");
        }
        List<PassageTrouve> passages = recherche.chercher(auteur.id(), question,
                demande.limite() == null ? LIMITE_PAR_DEFAUT : demande.limite());
        audit.consigner(auteur.id(), auteur.email(), ActionAudit.RECHERCHE, null, null, Map.of(
                "question", question,
                "documents", passages.stream().map(PassageTrouve::titre).distinct().toList()));
        return new Resultats(passages.stream().map(Extrait::de).toList());
    }

    /** Extrait cité par une réponse : 404 si le document est interdit, inconnu, ou si le passage n'en fait pas partie */
    @GetMapping("/api/documents/{document}/passages/{passage}")
    public Extrait extrait(@AuthenticationPrincipal UtilisateurConnecte auteur,
            @PathVariable UUID document, @PathVariable UUID passage) {
        return recherche.extrait(auteur.id(), document, passage)
                .map(Extrait::de)
                .orElseThrow(ErreurMetier::introuvable);
    }
}

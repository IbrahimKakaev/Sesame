package fr.sesame.questions;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.sesame.commun.ErreurMetier;
import fr.sesame.questions.DepotConversations.Conversation;
import fr.sesame.questions.DepotConversations.Detail;
import fr.sesame.questions.ServiceQuestions.Preparation;
import fr.sesame.securite.LimiteurDebit;
import fr.sesame.securite.UtilisateurConnecte;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ControleurQuestions - Questions en flux et historique des conversations
 *
 * La réponse à une question arrive en événements SSE : « texte » (fragment de la
 * réponse), « citation » (source vérifiée, à l'endroit où elle s'applique), puis « fin »
 * (texte définitif et identifiants) ou « erreur ».
 */
@RestController
public class ControleurQuestions {

    public record DemandeQuestion(@NotBlank @Size(max = 500) String question, UUID conversationId) {
    }

    private final ServiceQuestions questions;
    private final DepotConversations conversations;
    private final LimiteurDebit limite;
    private final ObjectMapper json;

    public ControleurQuestions(ServiceQuestions questions, DepotConversations conversations,
            @Qualifier("limiteQuestions") LimiteurDebit limite, ObjectMapper json) {
        this.questions = questions;
        this.conversations = conversations;
        this.limite = limite;
        this.json = json;
    }

    @PostMapping("/api/questions")
    public void poser(@AuthenticationPrincipal UtilisateurConnecte auteur, @Valid @RequestBody DemandeQuestion demande,
            HttpServletResponse reponse) throws IOException {
        limite.consommer(auteur.id().toString()).ifPresent(attente -> {
            throw ErreurMetier.tropDeRequetes(attente);
        });
        // Tout refus arrive ici, en JSON, avant le début du flux
        Preparation preparation = questions.preparer(auteur, demande.question(), demande.conversationId());

        reponse.setContentType("text/event-stream;charset=UTF-8");
        reponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        // Demande au serveur mandataire (nginx) de transmettre chaque événement sans attendre
        reponse.setHeader("X-Accel-Buffering", "no");
        PrintWriter sortie = reponse.getWriter();
        questions.diffuser(preparation, (evenement, donnees) -> {
            try {
                sortie.write("event: " + evenement + "\ndata: " + json.writeValueAsString(donnees) + "\n\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // Le navigateur est parti : inutile de continuer à payer la rédaction
            if (sortie.checkError()) {
                throw new UncheckedIOException(new IOException("Connexion fermée par le client"));
            }
        });
    }

    @GetMapping("/api/conversations")
    public List<Conversation> lister(@AuthenticationPrincipal UtilisateurConnecte auteur) {
        return conversations.de(auteur.id());
    }

    @GetMapping("/api/conversations/{id}")
    public Detail detail(@AuthenticationPrincipal UtilisateurConnecte auteur, @PathVariable UUID id) {
        return conversations.detail(id, auteur.id()).orElseThrow(ErreurMetier::introuvable);
    }

    @DeleteMapping("/api/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@AuthenticationPrincipal UtilisateurConnecte auteur, @PathVariable UUID id) {
        if (!conversations.supprimer(id, auteur.id())) {
            throw ErreurMetier.introuvable();
        }
    }
}

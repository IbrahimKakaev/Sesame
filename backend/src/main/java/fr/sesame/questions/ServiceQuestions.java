package fr.sesame.questions;

import fr.sesame.audit.ActionAudit;
import fr.sesame.audit.ServiceAudit;
import fr.sesame.commun.ErreurMetier;
import fr.sesame.questions.ClientClaude.CitationBrute;
import fr.sesame.questions.ClientClaude.Issue;
import fr.sesame.questions.ClientClaude.Source;
import fr.sesame.questions.ClientClaude.Texte;
import fr.sesame.recherche.ServiceRecherche;
import fr.sesame.recherche.ServiceRecherche.PassageTrouve;
import fr.sesame.securite.UtilisateurConnecte;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ServiceQuestions - Répond à une question à partir des documents autorisés
 *
 * Déroulé : recherche des passages parmi les seuls documents visibles par
 * l'utilisateur (ServiceRecherche), puis rédaction par le modèle à partir de ces
 * seuls passages, puis vérification de chaque citation avant qu'elle parte vers le
 * navigateur. Le filtrage par droits a donc lieu en base, avant tout appel au modèle.
 *
 * Chaque question est traitée seule : les échanges précédents ne sont pas renvoyés
 * au modèle. Un extrait vu hier dans un document retiré depuis ne peut ainsi pas
 * revenir par l'historique.
 */
@Service
public class ServiceQuestions {

    private static final Logger journal = LoggerFactory.getLogger(ServiceQuestions.class);
    private static final int LONGUEUR_TITRE = 80;

    /** Destinataire du flux de réponse (le navigateur, en événements SSE) */
    public interface Emetteur {
        void envoyer(String evenement, Object donnees);
    }

    /** Question acceptée, prête à être traitée : tous les refus possibles ont déjà eu lieu */
    public record Preparation(UtilisateurConnecte auteur, String question, UUID conversation,
            List<PassageTrouve> passages) {
    }

    public record EvenementTexte(String texte) {
    }

    public record EvenementCitation(int numero, UUID passageId, UUID documentId, String titre, int page,
            String extrait) {
    }

    public record EvenementFin(UUID conversationId, UUID messageId, String texte, boolean sansReponse) {
    }

    public record EvenementErreur(String erreur) {
    }

    private final ServiceRecherche recherche;
    private final ClientClaude claude;
    private final DepotConversations conversations;
    private final ServiceAudit audit;
    private final TransactionTemplate transaction;
    private final int nombreDePassages;
    private final double seuil;

    public ServiceQuestions(ServiceRecherche recherche, ClientClaude claude, DepotConversations conversations,
            ServiceAudit audit, TransactionTemplate transaction,
            @Value("${sesame.llm.passages}") int nombreDePassages,
            @Value("${sesame.recherche.seuil-similarite}") double seuil) {
        this.recherche = recherche;
        this.claude = claude;
        this.conversations = conversations;
        this.audit = audit;
        this.transaction = transaction;
        this.nombreDePassages = nombreDePassages;
        this.seuil = seuil;
    }

    /**
     * Contrôle la demande et cherche les passages. Les refus (conversation d'un autre,
     * rédaction indisponible) sont levés ici, avant que le flux de réponse ne commence.
     */
    public Preparation preparer(UtilisateurConnecte auteur, String questionEnvoyee, UUID conversation) {
        String question = ServiceRecherche.nettoyer(questionEnvoyee);
        if (question.isEmpty()) {
            throw ErreurMetier.requeteInvalide("Question vide");
        }
        // La conversation d'un autre utilisateur répond comme une conversation inexistante
        if (conversation != null && !conversations.appartient(conversation, auteur.id())) {
            throw ErreurMetier.introuvable();
        }
        List<PassageTrouve> passages = recherche.chercher(auteur.id(), question, nombreDePassages);
        // Sans passage assez proche de la question, inutile d'appeler le modèle
        boolean pertinents = passages.stream().anyMatch(passage -> passage.similarite() >= seuil);
        if (!pertinents) {
            passages = List.of();
        } else if (!claude.disponible()) {
            throw ErreurMetier.indisponible("La rédaction des réponses n'est pas configurée (clé d'API absente)");
        }
        return new Preparation(auteur, question, conversation, passages);
    }

    /** Rédige la réponse et l'envoie fragment par fragment, puis l'enregistre */
    public void diffuser(Preparation preparation, Emetteur emetteur) {
        List<PassageTrouve> fournis = preparation.passages();
        StringBuilder reponse = new StringBuilder();
        // Passages cités, dans l'ordre de première citation : le rang donne le numéro affiché
        Map<UUID, Integer> numeros = new LinkedHashMap<>();
        int[] citationsRejetees = {0};
        Issue issue = Issue.TERMINEE;

        if (!fournis.isEmpty()) {
            List<Source> sources = fournis.stream()
                    .map(passage -> new Source(passage.titre() + " (page " + passage.page() + ")", passage.contenu()))
                    .toList();
            try {
                issue = claude.rediger(preparation.question(), sources, fragment -> {
                    if (fragment instanceof Texte texte) {
                        reponse.append(texte.texte());
                        emetteur.envoyer("texte", new EvenementTexte(texte.texte()));
                    } else if (fragment instanceof CitationBrute citation) {
                        PassageTrouve passage = verifier(citation, fournis);
                        if (passage == null) {
                            citationsRejetees[0]++;
                            return;
                        }
                        int numero = numeros.computeIfAbsent(passage.id(), id -> numeros.size() + 1);
                        String marque = " [" + numero + "]";
                        // Plusieurs citations du même passage à la suite ne donnent qu'un renvoi
                        if (!reponse.toString().endsWith(marque)) {
                            reponse.append(marque);
                            emetteur.envoyer("citation", new EvenementCitation(numero, passage.id(),
                                    passage.documentId(), passage.titre(), passage.page(), passage.contenu()));
                        }
                    }
                });
            } catch (RuntimeException erreur) {
                journal.error("Rédaction de la réponse en échec", erreur);
                emetteur.envoyer("erreur", new EvenementErreur("La rédaction de la réponse a échoué"));
                return;
            }
        }
        if (citationsRejetees[0] > 0) {
            journal.warn("{} citation(s) rejetée(s) : elles ne correspondaient à aucun passage transmis",
                    citationsRejetees[0]);
        }

        // Une réponse sans aucune citation vérifiée ne s'appuie sur aucun document : elle n'est pas gardée
        boolean sansReponse = numeros.isEmpty() || issue == Issue.REFUSEE;
        if (sansReponse) {
            numeros.clear();
        }
        String texte = sansReponse ? ClientClaude.SANS_REPONSE : reponse.toString().strip();
        List<UUID> cites = new ArrayList<>(numeros.keySet());
        List<String> titresCites = fournis.stream().filter(passage -> numeros.containsKey(passage.id()))
                .map(PassageTrouve::titre).distinct().toList();

        UUID[] identifiants = new UUID[2];
        transaction.executeWithoutResult(etat -> {
            UtilisateurConnecte auteur = preparation.auteur();
            identifiants[0] = preparation.conversation() != null ? preparation.conversation()
                    : conversations.creer(auteur.id(), titre(preparation.question()));
            identifiants[1] = conversations.ajouterMessage(identifiants[0], preparation.question(), texte,
                    sansReponse, cites);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("question", preparation.question());
            details.put("documentsTransmis", fournis.stream().map(PassageTrouve::titre).distinct().toList());
            details.put("documentsCites", titresCites);
            details.put("citationsRejetees", citationsRejetees[0]);
            details.put("sansReponse", sansReponse);
            audit.consigner(auteur.id(), auteur.email(), ActionAudit.QUESTION_POSEE, "message", identifiants[1],
                    details);
        });
        emetteur.envoyer("fin", new EvenementFin(identifiants[0], identifiants[1], texte, sansReponse));
    }

    /**
     * Une citation n'est acceptée que si elle désigne un passage transmis au modèle pour
     * cette requête et si le texte qu'elle dit reprendre s'y trouve réellement.
     *
     * @return le passage cité, ou null si la citation est rejetée
     */
    static PassageTrouve verifier(CitationBrute citation, List<PassageTrouve> fournis) {
        if (citation.source() < 0 || citation.source() >= fournis.size()) {
            return null;
        }
        PassageTrouve passage = fournis.get(citation.source());
        String cite = sansEspaces(citation.texteCite());
        return !cite.isEmpty() && sansEspaces(passage.contenu()).contains(cite) ? passage : null;
    }

    private static String sansEspaces(String texte) {
        return texte == null ? "" : texte.replaceAll("\\s+", " ").strip();
    }

    private static String titre(String question) {
        return question.length() <= LONGUEUR_TITRE ? question : question.substring(0, LONGUEUR_TITRE - 1) + "…";
    }
}

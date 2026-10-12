package fr.sesame.questions;

import fr.sesame.documents.DepotDocuments;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * DepotConversations - Historique des questions d'un utilisateur
 *
 * Une conversation n'est lisible que par son auteur. Les citations sont relues avec
 * la règle d'accès du moment : si un droit a été retiré depuis la réponse, la source
 * correspondante n'est plus rendue.
 */
@Repository
public class DepotConversations {

    public record Conversation(UUID id, String titre, Instant creeLe) {
    }

    public record Citation(int numero, UUID passageId, UUID documentId, String titre, int page, String extrait) {
    }

    public record Message(UUID id, String question, String reponse, boolean sansReponse, Instant creeLe,
            List<Citation> citations) {
    }

    public record Detail(UUID id, String titre, Instant creeLe, List<Message> messages) {
    }

    private final JdbcClient sql;

    public DepotConversations(JdbcClient sql) {
        this.sql = sql;
    }

    public List<Conversation> de(UUID utilisateur) {
        return sql.sql("""
                        SELECT id, titre, cree_le FROM conversation
                        WHERE utilisateur_id = :utilisateur ORDER BY cree_le DESC, id
                        """)
                .param("utilisateur", utilisateur)
                .query((ligne, n) -> new Conversation(ligne.getObject("id", UUID.class), ligne.getString("titre"),
                        ligne.getTimestamp("cree_le").toInstant()))
                .list();
    }

    public boolean appartient(UUID conversation, UUID utilisateur) {
        return sql.sql("SELECT count(*) FROM conversation WHERE id = :id AND utilisateur_id = :utilisateur")
                .param("id", conversation)
                .param("utilisateur", utilisateur)
                .query(Integer.class)
                .single() == 1;
    }

    public Optional<Detail> detail(UUID conversation, UUID utilisateur) {
        Optional<Conversation> trouvee = de(utilisateur).stream().filter(c -> c.id().equals(conversation)).findFirst();
        if (trouvee.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, List<Citation>> citations = new LinkedHashMap<>();
        sql.sql("""
                        SELECT c.message_id, c.numero, p.id AS passage_id, d.id AS document_id, d.titre, p.page, p.contenu
                        FROM citation c
                        JOIN message m ON m.id = c.message_id
                        JOIN passage p ON p.id = c.passage_id
                        JOIN document d ON d.id = p.document_id
                        WHERE m.conversation_id = :conversation AND d.etat = 'INDEXE' AND
                        """ + DepotDocuments.VISIBLE + " ORDER BY c.numero")
                .param("conversation", conversation)
                .param("utilisateur", utilisateur)
                .query((ligne, n) -> {
                    citations.computeIfAbsent(ligne.getObject("message_id", UUID.class), id -> new ArrayList<>())
                            .add(new Citation(ligne.getInt("numero"), ligne.getObject("passage_id", UUID.class),
                                    ligne.getObject("document_id", UUID.class), ligne.getString("titre"),
                                    ligne.getInt("page"), ligne.getString("contenu")));
                    return n;
                })
                .list();
        List<Message> messages = sql.sql("""
                        SELECT id, question, reponse, sans_reponse, cree_le FROM message
                        WHERE conversation_id = :conversation ORDER BY cree_le, id
                        """)
                .param("conversation", conversation)
                .query((ligne, n) -> {
                    UUID id = ligne.getObject("id", UUID.class);
                    return new Message(id, ligne.getString("question"), ligne.getString("reponse"),
                            ligne.getBoolean("sans_reponse"), ligne.getTimestamp("cree_le").toInstant(),
                            citations.getOrDefault(id, List.of()));
                })
                .list();
        Conversation c = trouvee.get();
        return Optional.of(new Detail(c.id(), c.titre(), c.creeLe(), messages));
    }

    public UUID creer(UUID utilisateur, String titre) {
        UUID id = UUID.randomUUID();
        sql.sql("INSERT INTO conversation (id, utilisateur_id, titre) VALUES (:id, :utilisateur, :titre)")
                .param("id", id).param("utilisateur", utilisateur).param("titre", titre).update();
        return id;
    }

    /** @param passagesCites passages cités, dans l'ordre de leur numéro (le premier porte le numéro 1) */
    public UUID ajouterMessage(UUID conversation, String question, String reponse, boolean sansReponse,
            List<UUID> passagesCites) {
        UUID id = UUID.randomUUID();
        sql.sql("""
                        INSERT INTO message (id, conversation_id, question, reponse, sans_reponse)
                        VALUES (:id, :conversation, :question, :reponse, :sansReponse)
                        """)
                .param("id", id).param("conversation", conversation).param("question", question)
                .param("reponse", reponse).param("sansReponse", sansReponse).update();
        for (int i = 0; i < passagesCites.size(); i++) {
            // Un passage supprimé pendant la rédaction n'est simplement pas enregistré
            sql.sql("""
                            INSERT INTO citation (message_id, numero, passage_id)
                            SELECT :message, :numero, id FROM passage WHERE id = :passage
                            """)
                    .param("message", id).param("numero", i + 1).param("passage", passagesCites.get(i)).update();
        }
        return id;
    }

    public boolean supprimer(UUID conversation, UUID utilisateur) {
        return sql.sql("DELETE FROM conversation WHERE id = :id AND utilisateur_id = :utilisateur")
                .param("id", conversation).param("utilisateur", utilisateur).update() > 0;
    }
}

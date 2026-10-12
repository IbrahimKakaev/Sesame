package fr.sesame.documents;

import fr.sesame.documents.ExtracteurPdf.EchecExtraction;
import fr.sesame.documents.ServiceDepot.DocumentDepose;
import fr.sesame.embeddings.MoteurEmbeddings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Indexeur - Traitement en arrière-plan d'un document déposé
 *
 * Extraction du texte page par page, découpage en passages, calcul des embeddings,
 * enregistrement. Le document passe par les états en attente, en cours, puis
 * indexé ou en échec. Les passages et l'état « indexé » sont écrits dans la même
 * transaction : un document n'est jamais interrogeable à moitié.
 */
@Component
public class Indexeur {

    private static final Logger journal = LoggerFactory.getLogger(Indexeur.class);

    private record Passage(int page, String contenu, float[] embedding) {
    }

    private final JdbcTemplate sql;
    private final TransactionTemplate transaction;
    private final MoteurEmbeddings embeddings;
    private final ExtracteurPdf extracteur;
    private final Decoupeur decoupeur;

    public Indexeur(JdbcTemplate sql, TransactionTemplate transaction, MoteurEmbeddings embeddings,
            ProprietesDocuments proprietes) {
        this.sql = sql;
        this.transaction = transaction;
        this.embeddings = embeddings;
        this.extracteur = new ExtracteurPdf(proprietes.pagesMax(), proprietes.caracteresMax());
        this.decoupeur = new Decoupeur(proprietes.taillePassage(), proprietes.recouvrementPassage());
    }

    @Async("executeurIndexation")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void surDepot(DocumentDepose depot) {
        indexer(depot.id());
    }

    /** Au démarrage, reprend les documents dont l'indexation a été interrompue par un arrêt */
    @Async("executeurIndexation")
    @EventListener(ApplicationReadyEvent.class)
    public void reprendre() {
        sql.update("UPDATE document SET etat = 'EN_ATTENTE' WHERE etat = 'EN_COURS'");
        sql.queryForList("SELECT id FROM document WHERE etat = 'EN_ATTENTE' ORDER BY depose_le", UUID.class)
                .forEach(this::indexer);
    }

    void indexer(UUID id) {
        // Le passage à « en cours » réserve le document : un second traitement concurrent s'arrête ici
        if (sql.update("UPDATE document SET etat = 'EN_COURS' WHERE id = ? AND etat = 'EN_ATTENTE'", id) == 0) {
            return;
        }
        try {
            byte[] contenu = sql.queryForObject(
                    "SELECT contenu FROM document_fichier WHERE document_id = ?", byte[].class, id);
            List<String> pages = extracteur.extraire(contenu);
            List<Passage> passages = new ArrayList<>();
            for (int page = 0; page < pages.size(); page++) {
                for (String texte : decoupeur.decouper(pages.get(page))) {
                    passages.add(new Passage(page + 1, texte, embeddings.pourPassage(texte)));
                }
            }
            enregistrer(id, pages.size(), passages);
        } catch (EchecExtraction echec) {
            echouer(id, echec.getMessage());
        } catch (RuntimeException erreur) {
            journal.error("Indexation du document {} en échec", id, erreur);
            echouer(id, "Erreur interne pendant l'indexation");
        }
    }

    private void enregistrer(UUID id, int nombrePages, List<Passage> passages) {
        transaction.executeWithoutResult(etat -> {
            sql.update("DELETE FROM passage WHERE document_id = ?", id);
            List<Object[]> lignes = new ArrayList<>();
            for (int position = 0; position < passages.size(); position++) {
                Passage passage = passages.get(position);
                lignes.add(new Object[] {id, passage.page(), position, passage.contenu(),
                        MoteurEmbeddings.versSql(passage.embedding())});
            }
            sql.batchUpdate("""
                    INSERT INTO passage (document_id, page, position, contenu, embedding)
                    VALUES (?, ?, ?, ?, ?::vector)
                    """, lignes);
            sql.update("""
                    UPDATE document SET etat = 'INDEXE', nombre_pages = ?, motif_echec = NULL, indexe_le = now()
                    WHERE id = ?
                    """, nombrePages, id);
        });
    }

    private void echouer(UUID id, String motif) {
        sql.update("UPDATE document SET etat = 'EN_ECHEC', motif_echec = ? WHERE id = ?", motif, id);
    }
}

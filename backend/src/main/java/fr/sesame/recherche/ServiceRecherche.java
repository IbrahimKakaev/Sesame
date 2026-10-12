package fr.sesame.recherche;

import fr.sesame.documents.DepotDocuments;
import fr.sesame.embeddings.MoteurEmbeddings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * ServiceRecherche - Recherche des passages pertinents, parmi les seuls passages autorisés
 *
 * Toute la recherche tient en une requête SQL. Sa première étape, « autorises »,
 * ne garde que les passages des documents indexés que l'utilisateur a le droit de
 * voir (condition {@link DepotDocuments#VISIBLE}). Les deux branches de la recherche,
 * sémantique (vecteurs) et lexicale (plein texte français), ne lisent que cette
 * étape : un passage interdit n'est jamais classé, jamais compté, jamais renvoyé.
 * Le résultat ne dépend donc que des documents autorisés ; ajouter ou retirer un
 * document interdit ne le change pas d'un octet.
 *
 * Les deux classements sont fusionnés par rangs réciproques (RRF) : un passage
 * bien classé par l'une ou l'autre branche remonte, sans avoir à comparer une
 * similarité de vecteurs à un score de plein texte.
 */
@Service
public class ServiceRecherche {

    public enum Mode { HYBRIDE, VECTEURS }

    /** Passage retrouvé, avec sa similarité à la question (de -1 à 1) et son score de fusion */
    public record PassageTrouve(UUID id, UUID documentId, String titre, int page, String contenu,
            double similarite, double score) {
    }

    // Constante usuelle de la fusion par rangs réciproques
    private static final int K_RRF = 60;

    private static final String AUTORISES = """
            WITH autorises AS (
                SELECT p.id, p.document_id, p.page, p.contenu, p.embedding, p.recherche, d.titre
                FROM passage p
                JOIN document d ON d.id = p.document_id
                WHERE d.etat = 'INDEXE' AND
            """ + DepotDocuments.VISIBLE + ")";

    private static final String RECHERCHE = AUTORISES + """
            ,
            semantique AS (
                SELECT id, row_number() OVER (ORDER BY embedding <=> CAST(:vecteur AS vector), id) AS rang
                FROM autorises
                ORDER BY embedding <=> CAST(:vecteur AS vector), id
                LIMIT :candidats
            ),
            -- La question devient une requête plein texte où un seul mot commun suffit
            requete AS (
                SELECT NULLIF(replace(plainto_tsquery('french', :question)::text, '&', '|'), '')::tsquery AS mots
            ),
            lexicale AS (
                SELECT a.id, row_number() OVER (
                           ORDER BY ts_rank_cd(a.recherche, r.mots) DESC, a.embedding <=> CAST(:vecteur AS vector), a.id) AS rang
                FROM autorises a, requete r
                WHERE :hybride AND a.recherche @@ r.mots
                ORDER BY ts_rank_cd(a.recherche, r.mots) DESC, a.embedding <=> CAST(:vecteur AS vector), a.id
                LIMIT :candidats
            )
            SELECT a.id, a.document_id, a.titre, a.page, a.contenu,
                   1 - (a.embedding <=> CAST(:vecteur AS vector)) AS similarite,
                   COALESCE(1.0 / (:k + s.rang), 0) + COALESCE(1.0 / (:k + l.rang), 0) AS score
            FROM autorises a
            LEFT JOIN semantique s ON s.id = a.id
            LEFT JOIN lexicale l ON l.id = a.id
            WHERE s.id IS NOT NULL OR l.id IS NOT NULL
            -- À score égal (fréquent : les scores ne dépendent que des rangs), le passage le plus
            -- proche de la question passe devant. L'identifiant, tiré au hasard, ne départage qu'en dernier.
            ORDER BY score DESC, a.embedding <=> CAST(:vecteur AS vector), a.id
            LIMIT :limite
            """;

    private static final String EXTRAIT = AUTORISES + """
            SELECT a.id, a.document_id, a.titre, a.page, a.contenu, 1 AS similarite, 0 AS score
            FROM autorises a
            WHERE a.id = :passage AND a.document_id = :document
            """;

    private final JdbcClient sql;
    private final MoteurEmbeddings embeddings;
    private final Mode mode;
    private final int candidats;

    public ServiceRecherche(JdbcClient sql, MoteurEmbeddings embeddings,
            @Value("${sesame.recherche.mode}") Mode mode,
            @Value("${sesame.recherche.candidats}") int candidats) {
        this.sql = sql;
        this.embeddings = embeddings;
        this.mode = mode;
        this.candidats = candidats;
    }

    /**
     * Question débarrassée de ses caractères de contrôle (PostgreSQL refuse le caractère nul)
     * et de ses espaces superflus.
     */
    public static String nettoyer(String question) {
        return question.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
    }

    public List<PassageTrouve> chercher(UUID utilisateur, String question, int limite) {
        return chercher(utilisateur, question, limite, mode);
    }

    public List<PassageTrouve> chercher(UUID utilisateur, String question, int limite, Mode mode) {
        return sql.sql(RECHERCHE)
                .param("utilisateur", utilisateur)
                .param("vecteur", MoteurEmbeddings.versSql(embeddings.pourQuestion(question)))
                .param("question", question)
                .param("hybride", mode == Mode.HYBRIDE)
                .param("candidats", candidats)
                .param("k", K_RRF)
                .param("limite", limite)
                .query(ServiceRecherche::lire)
                .list();
    }

    /** Un passage précis d'un document, si l'utilisateur a le droit de le voir */
    public Optional<PassageTrouve> extrait(UUID utilisateur, UUID document, UUID passage) {
        return sql.sql(EXTRAIT)
                .param("utilisateur", utilisateur)
                .param("document", document)
                .param("passage", passage)
                .query(ServiceRecherche::lire)
                .optional();
    }

    private static PassageTrouve lire(java.sql.ResultSet ligne, int numero) throws java.sql.SQLException {
        return new PassageTrouve(
                ligne.getObject("id", UUID.class),
                ligne.getObject("document_id", UUID.class),
                ligne.getString("titre"),
                ligne.getInt("page"),
                ligne.getString("contenu"),
                ligne.getDouble("similarite"),
                ligne.getDouble("score"));
    }
}

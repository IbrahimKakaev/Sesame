package fr.sesame.documents;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * DepotDocuments - Accès en base aux documents
 *
 * La règle d'accès tient en une condition SQL, {@link #VISIBLE}, reprise par toutes
 * les lectures faites au nom d'un utilisateur (liste, détail, fichier, extrait,
 * recherche). Les groupes sont lus en base à chaque requête : il n'y a ni cache ni
 * copie des droits ailleurs, donc un droit retiré l'est dès la requête suivante.
 *
 * Les méthodes sans paramètre « utilisateur » ignorent cette règle : elles sont
 * réservées à l'administration et à l'indexation.
 */
@Repository
public class DepotDocuments {

    /**
     * Vrai si l'utilisateur :utilisateur partage au moins un groupe avec le document d.
     * Un document inexistant et un document interdit donnent le même résultat : aucune ligne.
     */
    public static final String VISIBLE = """
            EXISTS (SELECT 1
                    FROM document_groupe dg
                    JOIN utilisateur_groupe ug ON ug.groupe_id = dg.groupe_id
                    WHERE dg.document_id = d.id AND ug.utilisateur_id = :utilisateur)
            """;

    private static final String COLONNES = """
            SELECT d.id, d.titre, d.nom_fichier, d.taille_octets, d.nombre_pages, d.etat, d.motif_echec, d.depose_le,
                   ARRAY(SELECT g.nom FROM document_groupe dg2 JOIN groupe g ON g.id = dg2.groupe_id
                         WHERE dg2.document_id = d.id ORDER BY g.nom) AS groupes
            FROM document d
            """;

    /** Fichier d'origine d'un document */
    public record Fichier(String nom, byte[] contenu) {
    }

    private final JdbcClient sql;

    public DepotDocuments(JdbcClient sql) {
        this.sql = sql;
    }

    // ==================== LECTURES AU NOM D'UN UTILISATEUR ====================

    public List<DocumentResume> visiblesPar(UUID utilisateur) {
        return sql.sql(COLONNES + " WHERE " + VISIBLE + " ORDER BY d.depose_le DESC, d.titre")
                .param("utilisateur", utilisateur)
                .query(DepotDocuments::lire)
                .list();
    }

    public Optional<DocumentResume> visible(UUID id, UUID utilisateur) {
        return sql.sql(COLONNES + " WHERE d.id = :id AND " + VISIBLE)
                .param("id", id)
                .param("utilisateur", utilisateur)
                .query(DepotDocuments::lire)
                .optional();
    }

    public Optional<Fichier> fichierVisible(UUID id, UUID utilisateur) {
        return sql.sql("""
                        SELECT d.nom_fichier, f.contenu
                        FROM document d JOIN document_fichier f ON f.document_id = d.id
                        WHERE d.id = :id AND
                        """ + VISIBLE)
                .param("id", id)
                .param("utilisateur", utilisateur)
                .query((ligne, n) -> new Fichier(ligne.getString("nom_fichier"), ligne.getBytes("contenu")))
                .optional();
    }

    /** Groupes de l'utilisateur, relus en base */
    public List<UUID> groupesDe(UUID utilisateur) {
        return sql.sql("SELECT groupe_id FROM utilisateur_groupe WHERE utilisateur_id = :utilisateur")
                .param("utilisateur", utilisateur)
                .query(UUID.class)
                .list();
    }

    // ==================== ADMINISTRATION ET INDEXATION ====================

    public List<DocumentResume> tous() {
        return sql.sql(COLONNES + " ORDER BY d.depose_le DESC, d.titre").query(DepotDocuments::lire).list();
    }

    public Optional<DocumentResume> parId(UUID id) {
        return sql.sql(COLONNES + " WHERE d.id = :id").param("id", id).query(DepotDocuments::lire).optional();
    }

    public int nombreDeGroupesExistants(Collection<UUID> groupes) {
        if (groupes.isEmpty()) {
            return 0;
        }
        return sql.sql("SELECT count(*) FROM groupe WHERE id IN (:groupes)")
                .param("groupes", groupes)
                .query(Integer.class)
                .single();
    }

    public void creer(UUID id, String titre, String nomFichier, byte[] contenu, UUID deposePar,
            Collection<UUID> groupes) {
        sql.sql("""
                        INSERT INTO document (id, titre, nom_fichier, taille_octets, depose_par)
                        VALUES (:id, :titre, :nom, :taille, :deposePar)
                        """)
                .param("id", id)
                .param("titre", titre)
                .param("nom", nomFichier)
                .param("taille", contenu.length)
                .param("deposePar", deposePar)
                .update();
        sql.sql("INSERT INTO document_fichier (document_id, contenu) VALUES (:id, :contenu)")
                .param("id", id)
                .param("contenu", contenu)
                .update();
        remplacerGroupes(id, groupes);
    }

    public void remplacerGroupes(UUID document, Collection<UUID> groupes) {
        sql.sql("DELETE FROM document_groupe WHERE document_id = :id").param("id", document).update();
        for (UUID groupe : groupes) {
            sql.sql("INSERT INTO document_groupe (document_id, groupe_id) VALUES (:id, :groupe)")
                    .param("id", document)
                    .param("groupe", groupe)
                    .update();
        }
    }

    public boolean supprimer(UUID id) {
        return sql.sql("DELETE FROM document WHERE id = :id").param("id", id).update() > 0;
    }

    private static DocumentResume lire(ResultSet ligne, int numero) throws SQLException {
        Integer pages = ligne.getObject("nombre_pages", Integer.class);
        return new DocumentResume(
                ligne.getObject("id", UUID.class),
                ligne.getString("titre"),
                ligne.getString("nom_fichier"),
                ligne.getLong("taille_octets"),
                pages,
                EtatDocument.valueOf(ligne.getString("etat")),
                ligne.getString("motif_echec"),
                ligne.getTimestamp("depose_le").toInstant(),
                Arrays.asList((String[]) ligne.getArray("groupes").getArray()));
    }
}

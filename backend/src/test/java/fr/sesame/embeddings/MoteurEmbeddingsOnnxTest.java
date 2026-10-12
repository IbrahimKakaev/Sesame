package fr.sesame.embeddings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le vrai modèle d'embeddings, exécuté dans la JVM.
 * Ces tests demandent le modèle sur le disque (scripts/telecharger-modele.sh). S'il
 * manque, ils sont ignorés, sauf si SESAME_MODELE_OBLIGATOIRE=true (cas de la CI),
 * où son absence est une erreur.
 */
class MoteurEmbeddingsOnnxTest {

    public static final Path DOSSIER = Path.of("modeles/multilingual-e5-small");

    private static MoteurEmbeddingsOnnx moteur;

    /** Vrai si le modèle est présent ; échoue s'il est absent alors qu'il est exigé */
    public static boolean modeleDisponible() {
        boolean present = Files.isRegularFile(DOSSIER.resolve("model.onnx"));
        if (!present && "true".equals(System.getenv("SESAME_MODELE_OBLIGATOIRE"))) {
            throw new IllegalStateException("Modèle d'embeddings absent : lancer scripts/telecharger-modele.sh");
        }
        return present;
    }

    @BeforeAll
    static void charger() throws Exception {
        assumeTrue(modeleDisponible(), "modèle d'embeddings absent");
        moteur = new MoteurEmbeddingsOnnx(DOSSIER);
    }

    @AfterAll
    static void fermer() throws Exception {
        if (moteur != null) {
            moteur.close();
        }
    }

    private static double similarite(float[] a, float[] b) {
        double produit = 0;
        for (int i = 0; i < a.length; i++) {
            produit += a[i] * b[i];
        }
        return produit;
    }

    @Test
    @DisplayName("un vecteur a 384 dimensions et une norme de 1")
    void dimensionEtNorme() {
        float[] vecteur = moteur.pourPassage("Le télétravail est limité à deux jours par semaine.");

        assertThat(vecteur).hasSize(MoteurEmbeddings.DIMENSION);
        assertThat(similarite(vecteur, vecteur)).isCloseTo(1.0, within(1e-4));
    }

    @Test
    @DisplayName("le même texte donne toujours le même vecteur")
    void deterministe() {
        assertThat(moteur.pourQuestion("Combien de jours de congés ?"))
                .isEqualTo(moteur.pourQuestion("Combien de jours de congés ?"));
    }

    @Test
    @DisplayName("une question est plus proche du passage qui y répond que d'un passage hors sujet")
    void proximiteDeSens() {
        float[] question = moteur.pourQuestion("Combien gagne un développeur expérimenté ?");
        float[] salaires = moteur.pourPassage("Développeur senior : de 52 000 à 61 000 euros bruts par an.");
        float[] cantine = moteur.pourPassage("Le restaurant d'entreprise sera fermé pour travaux en novembre.");

        assertThat(similarite(question, salaires)).isGreaterThan(similarite(question, cantine) + 0.03);
    }

    @Test
    @DisplayName("un texte plus long que la limite du modèle est tronqué, pas refusé")
    void texteTresLong() {
        assertThat(moteur.pourPassage("procédure ".repeat(2000))).hasSize(MoteurEmbeddings.DIMENSION);
    }
}

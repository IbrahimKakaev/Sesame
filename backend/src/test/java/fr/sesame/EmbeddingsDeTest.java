package fr.sesame;

import fr.sesame.embeddings.MoteurEmbeddings;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Moteur d'embeddings des tests d'intégration.
 *
 * Chaque mot allume une case du vecteur : deux textes qui partagent des mots sont
 * proches. C'est volontairement naïf. Les tests d'étanchéité vérifient un filtrage par
 * droits, qui ne doit dépendre d'aucun modèle ; un moteur déterministe les rend rapides
 * et reproductibles. La qualité du vrai modèle est mesurée à part (EvaluationRechercheTest).
 */
@TestConfiguration
public class EmbeddingsDeTest {

    @Bean
    @ConditionalOnProperty(name = "sesame.embeddings.moteur", havingValue = "test")
    MoteurEmbeddings moteurEmbeddings() {
        return new MoteurEmbeddings() {
            @Override
            public float[] pourPassage(String texte) {
                return vecteur(texte);
            }

            @Override
            public float[] pourQuestion(String texte) {
                return vecteur(texte);
            }
        };
    }

    public static float[] vecteur(String texte) {
        float[] vecteur = new float[MoteurEmbeddings.DIMENSION];
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (String mot : sansAccents.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (mot.length() > 2) {
                vecteur[Math.floorMod(mot.hashCode(), vecteur.length)] += 1;
            }
        }
        double carres = 0;
        for (float valeur : vecteur) {
            carres += valeur * valeur;
        }
        if (carres == 0) {
            vecteur[0] = 1;
            return vecteur;
        }
        float norme = (float) Math.sqrt(carres);
        for (int i = 0; i < vecteur.length; i++) {
            vecteur[i] /= norme;
        }
        return vecteur;
    }
}

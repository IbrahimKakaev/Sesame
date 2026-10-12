package fr.sesame.embeddings;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * MoteurEmbeddingsOnnx - Modèle multilingual-e5-small exécuté dans la JVM
 *
 * Aucun service externe : le modèle (format ONNX) et son tokenizer sont lus sur
 * le disque. Le modèle attend un préfixe qui distingue questions et passages,
 * et son vecteur est la moyenne des vecteurs de tokens, normalisée.
 */
public class MoteurEmbeddingsOnnx implements MoteurEmbeddings, AutoCloseable {

    // Limite du modèle : au-delà, le texte est tronqué
    private static final int TOKENS_MAX = 512;

    private final OrtEnvironment environnement;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final boolean attendTypesDeTokens;

    public MoteurEmbeddingsOnnx(Path dossier) throws IOException, OrtException {
        Path modele = dossier.resolve("model.onnx");
        Path vocabulaire = dossier.resolve("tokenizer.json");
        if (!Files.isRegularFile(modele) || !Files.isRegularFile(vocabulaire)) {
            throw new IllegalStateException("Modèle d'embeddings introuvable dans " + dossier.toAbsolutePath()
                    + " : lancer scripts/telecharger-modele.sh");
        }
        this.environnement = OrtEnvironment.getEnvironment();
        this.session = environnement.createSession(modele.toString(), new OrtSession.SessionOptions());
        this.tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(vocabulaire)
                .optTruncation(true)
                .optMaxLength(TOKENS_MAX)
                .optPadding(false)
                .build();
        this.attendTypesDeTokens = session.getInputNames().contains("token_type_ids");
    }

    @Override
    public float[] pourPassage(String texte) {
        return calculer("passage: " + texte);
    }

    @Override
    public float[] pourQuestion(String texte) {
        return calculer("query: " + texte);
    }

    private float[] calculer(String texte) {
        Encoding tokens = tokenizer.encode(texte);
        long[] masque = tokens.getAttentionMask();
        Map<String, OnnxTensor> entrees = new HashMap<>();
        try {
            entrees.put("input_ids", OnnxTensor.createTensor(environnement, new long[][] {tokens.getIds()}));
            entrees.put("attention_mask", OnnxTensor.createTensor(environnement, new long[][] {masque}));
            if (attendTypesDeTokens) {
                entrees.put("token_type_ids", OnnxTensor.createTensor(environnement, new long[][] {tokens.getTypeIds()}));
            }
            try (OrtSession.Result resultat = session.run(entrees)) {
                // Sortie : un vecteur par token, [1][tokens][384]
                float[][][] parToken = (float[][][]) resultat.get(0).getValue();
                return normaliser(moyenne(parToken[0], masque));
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Calcul d'embedding impossible", e);
        } finally {
            entrees.values().forEach(OnnxTensor::close);
        }
    }

    private static float[] moyenne(float[][] parToken, long[] masque) {
        float[] somme = new float[DIMENSION];
        int comptes = 0;
        for (int token = 0; token < parToken.length; token++) {
            if (masque[token] == 0) {
                continue;
            }
            comptes++;
            for (int i = 0; i < DIMENSION; i++) {
                somme[i] += parToken[token][i];
            }
        }
        for (int i = 0; i < DIMENSION; i++) {
            somme[i] /= Math.max(1, comptes);
        }
        return somme;
    }

    static float[] normaliser(float[] vecteur) {
        double carres = 0;
        for (float valeur : vecteur) {
            carres += valeur * valeur;
        }
        float norme = (float) Math.sqrt(carres);
        if (norme > 0) {
            for (int i = 0; i < vecteur.length; i++) {
                vecteur[i] /= norme;
            }
        }
        return vecteur;
    }

    @Override
    public void close() throws OrtException {
        session.close();
        tokenizer.close();
    }
}

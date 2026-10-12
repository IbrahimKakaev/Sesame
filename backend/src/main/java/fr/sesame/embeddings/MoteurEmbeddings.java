package fr.sesame.embeddings;

/**
 * Transforme un texte en vecteur normalisé de {@link #DIMENSION} nombres.
 * Deux textes de sens proche donnent des vecteurs proches.
 */
public interface MoteurEmbeddings {

    int DIMENSION = 384;

    /** Vecteur d'un passage de document, calculé à l'indexation */
    float[] pourPassage(String texte);

    /** Vecteur d'une question, calculé à chaque recherche */
    float[] pourQuestion(String texte);

    /** Écriture attendue par pgvector : [0.1,0.2,...] */
    static String versSql(float[] vecteur) {
        StringBuilder texte = new StringBuilder(vecteur.length * 10).append('[');
        for (int i = 0; i < vecteur.length; i++) {
            if (i > 0) {
                texte.append(',');
            }
            texte.append(vecteur[i]);
        }
        return texte.append(']').toString();
    }
}

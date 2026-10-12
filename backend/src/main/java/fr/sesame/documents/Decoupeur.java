package fr.sesame.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decoupeur - Découpe le texte d'une page en passages
 *
 * Les passages suivent les phrases, visent une taille donnée et se recouvrent
 * légèrement : une information à cheval sur deux passages reste retrouvable.
 * Un passage ne franchit jamais une page, pour que sa citation porte un numéro exact.
 */
public class Decoupeur {

    // Pas de coupure après « : » ou « ; » : elle séparerait un intitulé de sa valeur
    private static final Pattern FIN_DE_PHRASE = Pattern.compile("(?<=[.!?…])\\s+|\\n{2,}");

    private final int taille;
    private final int recouvrement;

    public Decoupeur(int taille, int recouvrement) {
        this.taille = taille;
        this.recouvrement = Math.min(recouvrement, taille / 2);
    }

    public List<String> decouper(String texteDePage) {
        String texte = nettoyer(texteDePage);
        List<String> passages = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        for (String phrase : phrases(texte)) {
            if (courant.length() > 0 && courant.length() + 1 + phrase.length() > taille) {
                passages.add(courant.toString());
                String reprise = finDe(courant.toString());
                courant.setLength(0);
                // La reprise n'est gardée que si elle laisse la place à la phrase suivante
                if (reprise.length() + 1 + phrase.length() <= taille) {
                    courant.append(reprise);
                }
            }
            if (courant.length() > 0) {
                courant.append(' ');
            }
            courant.append(phrase);
        }
        if (!courant.isEmpty()) {
            passages.add(courant.toString());
        }
        return passages;
    }

    /** Phrases du texte ; une phrase plus longue qu'un passage est coupée net */
    private List<String> phrases(String texte) {
        List<String> phrases = new ArrayList<>();
        for (String phrase : FIN_DE_PHRASE.split(texte)) {
            String reste = phrase.replaceAll("\\s+", " ").strip();
            while (reste.length() > taille) {
                phrases.add(reste.substring(0, taille));
                reste = reste.substring(taille);
            }
            if (!reste.isEmpty()) {
                phrases.add(reste);
            }
        }
        return phrases;
    }

    /** Dernières phrases du passage, dans la limite du recouvrement */
    private String finDe(String passage) {
        if (recouvrement == 0 || passage.length() <= recouvrement) {
            return "";
        }
        String fin = passage.substring(passage.length() - recouvrement);
        var coupure = FIN_DE_PHRASE.matcher(fin);
        return coupure.find() ? fin.substring(coupure.end()).strip() : "";
    }

    private static String nettoyer(String texte) {
        return texte.replace("\r", "")
                .replace(' ', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\\n ?", "\n");
    }
}

package fr.sesame.documents;

import java.text.Normalizer;

/**
 * NomsDeFichiers - Assainit les noms et titres fournis à l'envoi d'un fichier
 *
 * Le nom envoyé par le navigateur est une donnée hostile : chemin (« ../../etc/passwd »),
 * caractères de contrôle, guillemets destinés à casser un en-tête HTTP, double extension.
 */
public final class NomsDeFichiers {

    private static final int LONGUEUR_MAX_NOM = 100;
    private static final int LONGUEUR_MAX_TITRE = 150;
    private static final String EXTENSION = ".pdf";

    private NomsDeFichiers() {
    }

    /** Nom sûr, toujours terminé par .pdf, sans chemin ni caractère spécial */
    public static String assainir(String nomEnvoye) {
        String nom = nomEnvoye == null ? "" : Normalizer.normalize(nomEnvoye, Normalizer.Form.NFC);
        // Seul le dernier segment du chemin compte, que le séparateur soit Unix ou Windows
        nom = nom.substring(Math.max(nom.lastIndexOf('/'), nom.lastIndexOf('\\')) + 1);
        nom = nom.replaceAll("[^\\p{L}\\p{N} ._()-]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("\\.{2,}", ".")
                .strip();
        if (nom.toLowerCase().endsWith(EXTENSION)) {
            nom = nom.substring(0, nom.length() - EXTENSION.length());
        }
        // Ni fichier caché ni nom réduit à des points
        nom = nom.replaceAll("^[. ]+", "").replaceAll("[. ]+$", "");
        if (nom.isEmpty()) {
            nom = "document";
        }
        if (nom.length() > LONGUEUR_MAX_NOM - EXTENSION.length()) {
            nom = nom.substring(0, LONGUEUR_MAX_NOM - EXTENSION.length());
        }
        return nom + EXTENSION;
    }

    /** Titre affiché : celui fourni, sinon le nom du fichier sans son extension */
    public static String titre(String titreEnvoye, String nomAssaini) {
        String titre = titreEnvoye == null ? "" : titreEnvoye.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ")
                .replaceAll("\\s+", " ").strip();
        if (titre.isEmpty()) {
            titre = nomAssaini.substring(0, nomAssaini.length() - EXTENSION.length()).replace('_', ' ').strip();
        }
        return titre.length() > LONGUEUR_MAX_TITRE ? titre.substring(0, LONGUEUR_MAX_TITRE) : titre;
    }
}

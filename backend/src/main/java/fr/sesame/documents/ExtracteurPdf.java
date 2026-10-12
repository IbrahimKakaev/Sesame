package fr.sesame.documents;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * ExtracteurPdf - Extrait le texte d'un PDF, page par page
 *
 * Le fichier est une donnée hostile : il peut être tronqué, chiffré, compter des
 * milliers de pages ou se décompresser en un texte gigantesque. Chaque cas donne
 * un échec d'indexation au motif explicite, sans jamais faire tomber le serveur.
 */
public class ExtracteurPdf {

    /** Échec prévu, dont le motif est affiché à l'utilisateur */
    public static class EchecExtraction extends Exception {
        public EchecExtraction(String motif) {
            super(motif);
        }
    }

    /** Arrête l'extraction dès que le texte dépasse le plafond */
    private static class TexteBorne extends StringWriter {
        private final int restant;

        TexteBorne(int restant) {
            this.restant = restant;
        }

        @Override
        public void write(String texte, int debut, int longueur) {
            if (getBuffer().length() + longueur > restant) {
                throw new TexteTropLong();
            }
            super.write(texte, debut, longueur);
        }

        @Override
        public void write(char[] texte, int debut, int longueur) {
            if (getBuffer().length() + longueur > restant) {
                throw new TexteTropLong();
            }
            super.write(texte, debut, longueur);
        }

        @Override
        public void write(String texte) {
            write(texte, 0, texte.length());
        }
    }

    private static class TexteTropLong extends RuntimeException {
    }

    private final int pagesMax;
    private final int caracteresMax;

    public ExtracteurPdf(int pagesMax, int caracteresMax) {
        this.pagesMax = pagesMax;
        this.caracteresMax = caracteresMax;
    }

    /** @return le texte de chaque page, dans l'ordre (l'indice 0 est la page 1) */
    public List<String> extraire(byte[] octets) throws EchecExtraction {
        try (PDDocument document = Loader.loadPDF(octets)) {
            if (document.isEncrypted()) {
                throw new EchecExtraction("Le PDF est protégé");
            }
            int nombre = document.getNumberOfPages();
            if (nombre == 0) {
                throw new EchecExtraction("Le PDF ne contient aucune page");
            }
            if (nombre > pagesMax) {
                throw new EchecExtraction("Le PDF dépasse " + pagesMax + " pages");
            }
            List<String> pages = new ArrayList<>(nombre);
            PDFTextStripper lecteur = new PDFTextStripper();
            int restant = caracteresMax;
            for (int page = 1; page <= nombre; page++) {
                lecteur.setStartPage(page);
                lecteur.setEndPage(page);
                TexteBorne texte = new TexteBorne(restant);
                lecteur.writeText(document, texte);
                restant -= texte.getBuffer().length();
                pages.add(texte.toString());
            }
            if (pages.stream().allMatch(String::isBlank)) {
                throw new EchecExtraction("Le PDF ne contient aucun texte exploitable (document scanné ?)");
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw new EchecExtraction("Le PDF est protégé");
        } catch (TexteTropLong e) {
            throw new EchecExtraction("Le texte du PDF est trop volumineux");
        } catch (IOException | RuntimeException e) {
            // Le détail de l'erreur reste dans les journaux du serveur, pas dans la réponse
            throw new EchecExtraction("Le PDF est illisible");
        }
    }
}

package fr.sesame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Fabrique les PDF des tests : sains, chiffrés, sans texte ou trop longs */
public final class FabriquePdf {

    private static final int CARACTERES_PAR_LIGNE = 90;

    private FabriquePdf() {
    }

    /** PDF dont chaque argument est le texte d'une page */
    public static byte[] pdf(String... pages) {
        try (PDDocument document = composer(pages)) {
            return octets(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF qui demande un mot de passe à l'ouverture */
    public static byte[] pdfChiffre(String texte) {
        try (PDDocument document = composer(texte)) {
            StandardProtectionPolicy protection =
                    new StandardProtectionPolicy("proprietaire", "utilisateur", new AccessPermission());
            protection.setEncryptionKeyLength(128);
            document.protect(protection);
            return octets(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF de pages blanches, comme un document scanné sans couche de texte */
    public static byte[] pdfSansTexte(int nombreDePages) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < nombreDePages; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            return octets(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static PDDocument composer(String... pages) throws IOException {
        PDDocument document = new PDDocument();
        PDType1Font police = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (String texte : pages) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream contenu = new PDPageContentStream(document, page)) {
                contenu.beginText();
                contenu.setFont(police, 11);
                contenu.setLeading(15);
                contenu.newLineAtOffset(50, 780);
                for (String ligne : lignes(texte)) {
                    contenu.showText(ligne);
                    contenu.newLine();
                }
                contenu.endText();
            }
        }
        return document;
    }

    private static List<String> lignes(String texte) {
        List<String> lignes = new ArrayList<>();
        for (String paragraphe : texte.split("\n")) {
            StringBuilder ligne = new StringBuilder();
            for (String mot : paragraphe.split(" ")) {
                if (ligne.length() + mot.length() + 1 > CARACTERES_PAR_LIGNE) {
                    lignes.add(ligne.toString());
                    ligne.setLength(0);
                }
                ligne.append(ligne.isEmpty() ? "" : " ").append(mot);
            }
            lignes.add(ligne.toString());
        }
        return lignes;
    }

    private static byte[] octets(PDDocument document) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        document.save(sortie);
        return sortie.toByteArray();
    }
}

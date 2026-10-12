package fr.sesame.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * GenerateurPdfDemo - Fabrique les PDF de démonstration à partir de leurs sources en texte
 *
 * Les sources (demo-donnees/sources) sont rédigées à la main pour une entreprise
 * fictive. Format : une ligne « # Titre », une ligne « Diffusion : ... », puis un
 * paragraphe par ligne ; « === page === » passe à la page suivante.
 *
 * Usage, depuis backend/ :
 *   mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *       -Dexec.mainClass=fr.sesame.demo.GenerateurPdfDemo
 */
public final class GenerateurPdfDemo {

    private static final String SAUT_DE_PAGE = "=== page ===";
    private static final float MARGE = 62;
    private static final float LARGEUR = PDRectangle.A4.getWidth() - 2 * MARGE;
    private static final float HAUT = PDRectangle.A4.getHeight() - 70;

    private GenerateurPdfDemo() {
    }

    public static void main(String[] arguments) throws IOException {
        Path sources = Path.of(arguments.length > 0 ? arguments[0] : "../demo-donnees/sources");
        Path sortie = Path.of(arguments.length > 1 ? arguments[1] : "src/main/resources/demo/documents");
        Files.createDirectories(sortie);
        try (Stream<Path> fichiers = Files.list(sources)) {
            for (Path source : fichiers.filter(f -> f.toString().endsWith(".txt")).sorted().toList()) {
                String nom = source.getFileName().toString().replace(".txt", ".pdf");
                Files.write(sortie.resolve(nom), composer(Files.readAllLines(source)));
                System.out.println("généré : " + nom);
            }
        }
    }

    static byte[] composer(List<String> lignes) throws IOException {
        PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont gras = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont italique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        String titre = lignes.get(0).substring(2);

        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation informations = new PDDocumentInformation();
            informations.setTitle(titre);
            informations.setAuthor("Hélianthe Ingénierie (entreprise fictive)");
            // Date fixe : régénérer les PDF ne change pas leurs métadonnées
            informations.setCreationDate(new GregorianCalendar(2026, Calendar.OCTOBER, 1));
            document.setDocumentInformation(informations);

            Page page = new Page(document);
            page.ecrire("Hélianthe Ingénierie", gras, 9, 22);
            for (String ligne : couper(titre, gras, 17)) {
                page.ecrire(ligne, gras, 17, 22);
            }
            for (String ligne : lignes.subList(1, lignes.size())) {
                if (ligne.equals(SAUT_DE_PAGE)) {
                    page.fermer();
                    page = new Page(document);
                } else if (ligne.startsWith("Diffusion :")) {
                    page.ecrire(ligne, italique, 10, 26);
                } else if (!ligne.isBlank()) {
                    for (String morceau : couper(ligne, normal, 11)) {
                        page.ecrire(morceau, normal, 11, 15.5f);
                    }
                    page.descendre(8);
                }
            }
            page.fermer();
            var octets = new java.io.ByteArrayOutputStream();
            document.save(octets);
            return octets.toByteArray();
        }
    }

    /** Page en cours d'écriture, du haut vers le bas */
    private static final class Page {
        private final PDPageContentStream contenu;
        private float y = HAUT;

        Page(PDDocument document) throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contenu = new PDPageContentStream(document, page);
        }

        void ecrire(String texte, PDFont police, float taille, float interligne) throws IOException {
            contenu.beginText();
            contenu.setFont(police, taille);
            contenu.newLineAtOffset(MARGE, y);
            contenu.showText(texte);
            contenu.endText();
            y -= interligne;
        }

        void descendre(float hauteur) {
            y -= hauteur;
        }

        void fermer() throws IOException {
            contenu.close();
        }
    }

    /** Coupe un paragraphe en lignes qui tiennent dans la largeur de la page */
    private static List<String> couper(String paragraphe, PDFont police, float taille) throws IOException {
        List<String> lignes = new ArrayList<>();
        StringBuilder ligne = new StringBuilder();
        for (String mot : paragraphe.split(" ")) {
            String essai = ligne.isEmpty() ? mot : ligne + " " + mot;
            if (police.getStringWidth(essai) / 1000 * taille > LARGEUR && !ligne.isEmpty()) {
                lignes.add(ligne.toString());
                ligne.setLength(0);
                ligne.append(mot);
            } else {
                ligne.setLength(0);
                ligne.append(essai);
            }
        }
        lignes.add(ligne.toString());
        return lignes;
    }
}

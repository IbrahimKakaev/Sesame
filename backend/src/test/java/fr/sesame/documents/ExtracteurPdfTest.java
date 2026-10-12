package fr.sesame.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.sesame.FabriquePdf;
import fr.sesame.documents.ExtracteurPdf.EchecExtraction;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtracteurPdfTest {

    @Test
    @DisplayName("le texte est rendu page par page, accents compris")
    void pageParPage() throws EchecExtraction {
        List<String> pages = new ExtracteurPdf(10, 10_000)
                .extraire(FabriquePdf.pdf("Première page, données chiffrées.", "Seconde page."));

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0)).contains("Première page, données chiffrées.");
        assertThat(pages.get(1)).contains("Seconde page.");
    }

    @Test
    @DisplayName("l'extraction s'arrête dès que le texte dépasse le plafond")
    void texteTropVolumineux() {
        byte[] pdf = FabriquePdf.pdf("mot ".repeat(200), "mot ".repeat(200));

        assertThatThrownBy(() -> new ExtracteurPdf(10, 1_000).extraire(pdf))
                .isInstanceOf(EchecExtraction.class)
                .hasMessage("Le texte du PDF est trop volumineux");
    }

    @Test
    @DisplayName("des octets quelconques donnent un échec propre, pas une exception technique")
    void octetsQuelconques() {
        assertThatThrownBy(() -> new ExtracteurPdf(10, 1_000).extraire(new byte[] {1, 2, 3}))
                .isInstanceOf(EchecExtraction.class)
                .hasMessage("Le PDF est illisible");
    }
}

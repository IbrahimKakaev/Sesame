package fr.sesame.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecoupeurTest {

    private final Decoupeur decoupeur = new Decoupeur(100, 40);

    @Test
    @DisplayName("un texte court tient en un seul passage, espaces et retours à la ligne normalisés")
    void texteCourt() {
        assertThat(decoupeur.decouper("Le télétravail  est limité\nà deux jours.\r\n"))
                .containsExactly("Le télétravail est limité à deux jours.");
    }

    @Test
    @DisplayName("un texte vide ne donne aucun passage")
    void texteVide() {
        assertThat(decoupeur.decouper("  \n\n ")).isEmpty();
    }

    @Test
    @DisplayName("les passages coupent entre deux phrases et ne dépassent jamais la taille visée")
    void coupeEntreLesPhrases() {
        String texte = "Première phrase assez longue pour remplir le passage. "
                + "Deuxième phrase assez longue elle aussi, vraiment. "
                + "Troisième phrase pour finir le paragraphe en beauté.";

        List<String> passages = decoupeur.decouper(texte);

        assertThat(passages).hasSizeGreaterThan(1).allSatisfy(passage -> {
            assertThat(passage.length()).isLessThanOrEqualTo(100);
            assertThat(passage).endsWith(".");
        });
        assertThat(passages.get(0)).startsWith("Première phrase");
    }

    @Test
    @DisplayName("deux passages voisins se recouvrent : la dernière phrase de l'un ouvre le suivant")
    void recouvrement() {
        String texte = "Le plafond est de 40 euros. Le repas du midi est remboursé. "
                + "Le justificatif est obligatoire pour chaque dépense engagée. Le délai est de trente jours.";

        List<String> passages = decoupeur.decouper(texte);

        assertThat(passages).hasSize(3);
        assertThat(passages.get(0)).endsWith("Le repas du midi est remboursé.");
        assertThat(passages.get(1)).startsWith("Le repas du midi est remboursé.");
    }

    @Test
    @DisplayName("une phrase plus longue qu'un passage est coupée sans perdre de texte")
    void phraseTropLongue() {
        String phrase = "mot ".repeat(80).strip();

        List<String> passages = new Decoupeur(100, 0).decouper(phrase);

        assertThat(passages).allSatisfy(passage -> assertThat(passage.length()).isLessThanOrEqualTo(100));
        assertThat(String.join("", passages).replace(" ", "")).isEqualTo(phrase.replace(" ", ""));
    }
}

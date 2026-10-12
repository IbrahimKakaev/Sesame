package fr.sesame.securite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimiteurDebitTest {

    /** Horloge que le test avance à la main */
    private static class HorlogeReglable extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void avancer(Duration duree) {
            instant = instant.plus(duree);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final HorlogeReglable horloge = new HorlogeReglable();
    private final LimiteurDebit limiteur = new LimiteurDebit(3, Duration.ofMinutes(15), horloge);

    @Test
    @DisplayName("les essais au-delà de la limite sont refusés, avec le délai restant")
    void refuseAuDela() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiteur.consommer("a")).isEmpty();
        }
        horloge.avancer(Duration.ofMinutes(5));

        assertThat(limiteur.consommer("a")).contains(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("chaque clé a son propre compteur")
    void clesIndependantes() {
        for (int i = 0; i < 4; i++) {
            limiteur.consommer("a");
        }

        assertThat(limiteur.consommer("b")).isEmpty();
    }

    @Test
    @DisplayName("la fenêtre écoulée, les essais sont de nouveau autorisés")
    void fenetreEcoulee() {
        for (int i = 0; i < 4; i++) {
            limiteur.consommer("a");
        }
        horloge.avancer(Duration.ofMinutes(15));

        assertThat(limiteur.consommer("a")).isEmpty();
    }

    @Test
    @DisplayName("oublier une clé remet son compteur à zéro")
    void oublier() {
        for (int i = 0; i < 4; i++) {
            limiteur.consommer("a");
        }
        limiteur.oublier("a");

        assertThat(limiteur.consommer("a")).isEmpty();
    }
}

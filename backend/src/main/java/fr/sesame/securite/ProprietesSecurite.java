package fr.sesame.securite;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Réglages de sécurité (bloc sesame.securite de la configuration) */
@Validated
@ConfigurationProperties("sesame.securite")
public record ProprietesSecurite(
        // HS256 demande une clé d'au moins 256 bits
        @NotBlank(message = "SESAME_JWT_SECRET est obligatoire")
        @Size(min = 32, message = "SESAME_JWT_SECRET doit contenir au moins 32 caractères")
        String jwtSecret,
        @NotNull Duration dureeJetonAcces,
        @NotNull Duration dureeRenouvellement,
        boolean cookieSecurise,
        @Min(4) @Max(16) int coutBcrypt,
        @NotNull Connexion connexion) {

    /** Limite de débit des tentatives de connexion, par e-mail et par adresse IP */
    public record Connexion(@Min(1) int tentativesMax, @Min(1) int tentativesMaxParAdresse, @NotNull Duration fenetre) {
    }
}

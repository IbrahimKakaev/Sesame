package fr.sesame.documents;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** Limites appliquées aux dépôts et réglages du découpage (bloc sesame.documents) */
@Validated
@ConfigurationProperties("sesame.documents")
public record ProprietesDocuments(
        @NotNull DataSize tailleMax,
        @Min(1) int pagesMax,
        // Plafond du texte extrait, contre les PDF qui se décompressent en gigaoctets
        @Min(1) int caracteresMax,
        @Min(100) int taillePassage,
        @Min(0) int recouvrementPassage) {
}

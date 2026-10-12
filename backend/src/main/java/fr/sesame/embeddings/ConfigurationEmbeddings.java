package fr.sesame.embeddings;

import ai.onnxruntime.OrtException;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigurationEmbeddings {

    // Les tests d'étanchéité remplacent ce moteur par un moteur déterministe (sesame.embeddings.moteur=test)
    @Bean
    @ConditionalOnProperty(name = "sesame.embeddings.moteur", havingValue = "onnx", matchIfMissing = true)
    MoteurEmbeddings moteurEmbeddings(@Value("${sesame.embeddings.dossier-modele}") Path dossier)
            throws IOException, OrtException {
        return new MoteurEmbeddingsOnnx(dossier);
    }
}

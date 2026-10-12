package fr.sesame.documents;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ConfigurationIndexation {

    /** Un seul document indexé à la fois : le calcul des embeddings occupe déjà tous les cœurs */
    @Bean
    Executor executeurIndexation() {
        ThreadPoolTaskExecutor executeur = new ThreadPoolTaskExecutor();
        executeur.setCorePoolSize(1);
        executeur.setMaxPoolSize(1);
        executeur.setQueueCapacity(500);
        executeur.setThreadNamePrefix("indexation-");
        return executeur;
    }
}

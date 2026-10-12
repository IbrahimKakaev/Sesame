package fr.sesame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Sésame - Recherche documentaire d'entreprise avec cloisonnement des droits d'accès
 */
// Les comptes viennent de la base : pas d'utilisateur par défaut généré par Spring Security
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SesameApplication {

    public static void main(String[] args) {
        SpringApplication.run(SesameApplication.class, args);
    }
}

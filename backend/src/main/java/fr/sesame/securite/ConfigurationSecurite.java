package fr.sesame.securite;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.sesame.commun.ErreurApi;
import fr.sesame.comptes.DepotUtilisateurs;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ConfigurationSecurite - Règles d'accès de l'API
 *
 * Tout est refusé par défaut : une route doit être listée ici pour être publique.
 * L'API est sans état (pas de session serveur), donc sans jeton CSRF : aucune
 * route protégée ne s'authentifie par cookie.
 */
@Configuration
public class ConfigurationSecurite {

    @Bean
    SecurityFilterChain chaineSecurite(HttpSecurity http, ServiceJeton serviceJeton,
            DepotUtilisateurs utilisateurs, ObjectMapper json) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regles -> regles
                        // Rendu des erreurs : la requête d'origine a déjà été contrôlée
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/connexion", "/api/auth/renouvellement", "/api/auth/deconnexion")
                        .permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRATEUR")
                        .requestMatchers(HttpMethod.POST, "/api/documents").hasRole("CONTRIBUTEUR")
                        .anyRequest().authenticated())
                .exceptionHandling(erreurs -> erreurs
                        .authenticationEntryPoint((requete, reponse, e) ->
                                ecrire(reponse, json, HttpStatus.UNAUTHORIZED, "Non authentifié"))
                        .accessDeniedHandler((requete, reponse, e) ->
                                ecrire(reponse, json, HttpStatus.FORBIDDEN, "Accès refusé")))
                .addFilterBefore(new FiltreJeton(serviceJeton, utilisateurs),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Un administrateur peut tout ce que peut un contributeur, qui peut tout ce que peut un lecteur */
    @Bean
    RoleHierarchy hierarchieRoles() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMINISTRATEUR").implies("CONTRIBUTEUR")
                .role("CONTRIBUTEUR").implies("LECTEUR")
                .build();
    }

    @Bean
    PasswordEncoder encodeurMotDePasse(ProprietesSecurite proprietes) {
        return new BCryptPasswordEncoder(proprietes.coutBcrypt());
    }

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    LimiteurDebit limiteConnexionParEmail(ProprietesSecurite proprietes, Clock horloge) {
        var connexion = proprietes.connexion();
        return new LimiteurDebit(connexion.tentativesMax(), connexion.fenetre(), horloge);
    }

    @Bean
    LimiteurDebit limiteConnexionParAdresse(ProprietesSecurite proprietes, Clock horloge) {
        var connexion = proprietes.connexion();
        return new LimiteurDebit(connexion.tentativesMaxParAdresse(), connexion.fenetre(), horloge);
    }

    /** Questions et recherches, par compte */
    @Bean
    LimiteurDebit limiteQuestions(@Value("${sesame.questions.par-minute}") int parMinute, Clock horloge) {
        return new LimiteurDebit(parMinute, Duration.ofMinutes(1), horloge);
    }

    private static void ecrire(HttpServletResponse reponse, ObjectMapper json, HttpStatus statut, String message)
            throws IOException {
        reponse.setStatus(statut.value());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        json.writeValue(reponse.getWriter(), new ErreurApi(message));
    }
}

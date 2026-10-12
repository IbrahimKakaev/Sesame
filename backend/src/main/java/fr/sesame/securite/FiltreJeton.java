package fr.sesame.securite;

import fr.sesame.comptes.DepotUtilisateurs;
import fr.sesame.comptes.Utilisateur;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * FiltreJeton - Authentifie la requête à partir de l'en-tête « Authorization: Bearer »
 *
 * Le compte est relu en base à chaque requête : un compte désactivé ou un rôle
 * modifié prend effet immédiatement, même si le jeton est encore valide.
 * Un jeton absent, expiré ou falsifié laisse la requête anonyme (réponse 401).
 */
public class FiltreJeton extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final ServiceJeton serviceJeton;
    private final DepotUtilisateurs utilisateurs;

    public FiltreJeton(ServiceJeton serviceJeton, DepotUtilisateurs utilisateurs) {
        this.serviceJeton = serviceJeton;
        this.utilisateurs = utilisateurs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String entete = requete.getHeader(HttpHeaders.AUTHORIZATION);
        if (entete != null && entete.startsWith(PREFIXE)) {
            serviceJeton.lire(entete.substring(PREFIXE.length()))
                    .flatMap(utilisateurs::findById)
                    .filter(Utilisateur::isActif)
                    .ifPresent(this::authentifier);
        }
        chaine.doFilter(requete, reponse);
    }

    private void authentifier(Utilisateur utilisateur) {
        var auteur = new UtilisateurConnecte(utilisateur.getId(), utilisateur.getEmail(), utilisateur.getRole());
        var droits = List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name()));
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(auteur, null, droits));
        SecurityContextHolder.setContext(contexte);
    }
}

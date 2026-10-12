package fr.sesame.securite;

import fr.sesame.comptes.Role;
import java.util.UUID;

/** Auteur de la requête en cours, relu en base à chaque requête par {@link FiltreJeton} */
public record UtilisateurConnecte(UUID id, String email, Role role) {
}

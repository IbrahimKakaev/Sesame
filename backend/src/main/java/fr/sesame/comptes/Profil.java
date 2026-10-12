package fr.sesame.comptes;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Ce que l'API expose d'un compte : jamais l'empreinte du mot de passe */
public record Profil(UUID id, String email, String nom, Role role, boolean actif, List<GroupeResume> groupes) {

    public record GroupeResume(UUID id, String nom, boolean systeme) {

        public static GroupeResume de(Groupe groupe) {
            return new GroupeResume(groupe.getId(), groupe.getNom(), groupe.isSysteme());
        }
    }

    public static Profil de(Utilisateur utilisateur) {
        List<GroupeResume> groupes = utilisateur.getGroupes().stream()
                .map(GroupeResume::de)
                .sorted(Comparator.comparing(GroupeResume::nom))
                .toList();
        return new Profil(utilisateur.getId(), utilisateur.getEmail(), utilisateur.getNom(),
                utilisateur.getRole(), utilisateur.isActif(), groupes);
    }
}

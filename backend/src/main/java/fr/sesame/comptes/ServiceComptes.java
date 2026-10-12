package fr.sesame.comptes;

import fr.sesame.audit.ActionAudit;
import fr.sesame.audit.ServiceAudit;
import fr.sesame.commun.ErreurMetier;
import fr.sesame.comptes.Profil.GroupeResume;
import fr.sesame.securite.DepotJetonsRenouvellement;
import fr.sesame.securite.UtilisateurConnecte;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ServiceComptes - Gestion des comptes, des groupes et des appartenances
 *
 * Chaque modification est consignée dans le journal d'audit, avec l'état des
 * droits avant et après.
 */
@Service
public class ServiceComptes {

    // bcrypt ignore tout ce qui dépasse 72 octets
    private static final int OCTETS_MAX_MOT_DE_PASSE = 72;
    public static final int LONGUEUR_MIN_MOT_DE_PASSE = 10;

    public record CreationCompte(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String nom,
            @NotNull @Size(min = LONGUEUR_MIN_MOT_DE_PASSE, max = 72) String motDePasse,
            @NotNull Role role,
            Set<UUID> groupes) {
    }

    /** Remplace l'état du compte ; le mot de passe n'est changé que s'il est fourni */
    public record ModificationCompte(
            @NotBlank @Size(max = 100) String nom,
            @NotNull Role role,
            @NotNull Boolean actif,
            @NotNull Set<UUID> groupes,
            @Size(min = LONGUEUR_MIN_MOT_DE_PASSE, max = 72) String motDePasse) {
    }

    public record NomGroupe(@NotBlank @Size(max = 60) String nom) {
    }

    private final DepotUtilisateurs utilisateurs;
    private final DepotGroupes groupes;
    private final DepotJetonsRenouvellement jetons;
    private final ServiceAudit audit;
    private final PasswordEncoder encodeur;

    public ServiceComptes(DepotUtilisateurs utilisateurs, DepotGroupes groupes, DepotJetonsRenouvellement jetons,
            ServiceAudit audit, PasswordEncoder encodeur) {
        this.utilisateurs = utilisateurs;
        this.groupes = groupes;
        this.jetons = jetons;
        this.audit = audit;
        this.encodeur = encodeur;
    }

    public static String normaliserEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ==================== COMPTES ====================

    @Transactional(readOnly = true)
    public Profil profil(UUID id) {
        return utilisateurs.findById(id).map(Profil::de).orElseThrow(ErreurMetier::introuvable);
    }

    @Transactional(readOnly = true)
    public List<Profil> lister() {
        return utilisateurs.findAll().stream()
                .map(Profil::de)
                .sorted(Comparator.comparing(Profil::email))
                .toList();
    }

    @Transactional
    public Profil creer(UtilisateurConnecte acteur, CreationCompte demande) {
        String email = normaliserEmail(demande.email());
        if (utilisateurs.existsByEmail(email)) {
            throw ErreurMetier.conflit("Un compte utilise déjà cet e-mail");
        }
        Utilisateur utilisateur = new Utilisateur(email, demande.nom().trim(),
                hacher(demande.motDePasse()), demande.role());
        utilisateur.getGroupes().addAll(resoudre(demande.groupes()));
        utilisateurs.save(utilisateur);

        audit.consigner(acteur == null ? null : acteur.id(), acteur == null ? null : acteur.email(),
                ActionAudit.COMPTE_CREE, "utilisateur", utilisateur.getId(), etat(utilisateur));
        return Profil.de(utilisateur);
    }

    @Transactional
    public Profil modifier(UtilisateurConnecte acteur, UUID id, ModificationCompte demande) {
        Utilisateur utilisateur = utilisateurs.findById(id).orElseThrow(ErreurMetier::introuvable);
        // Garde-fou : un administrateur ne peut pas se retirer lui-même l'administration
        if (acteur.id().equals(id) && (demande.role() != Role.ADMINISTRATEUR || !demande.actif())) {
            throw ErreurMetier.conflit("Impossible de retirer ses propres droits d'administration");
        }
        Map<String, Object> avant = etat(utilisateur);
        boolean accesReduit = demande.role() != utilisateur.getRole() || !demande.actif();

        utilisateur.setNom(demande.nom().trim());
        utilisateur.setRole(demande.role());
        utilisateur.setActif(demande.actif());
        utilisateur.getGroupes().clear();
        utilisateur.getGroupes().addAll(resoudre(demande.groupes()));
        if (demande.motDePasse() != null) {
            utilisateur.setMotDePasse(hacher(demande.motDePasse()));
        }
        // Les sessions ouvertes ne survivent ni à un changement de rôle ni à un nouveau mot de passe
        if (accesReduit || demande.motDePasse() != null) {
            jetons.revoquerPourUtilisateur(id);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("avant", avant);
        details.put("apres", etat(utilisateur));
        details.put("motDePasseChange", demande.motDePasse() != null);
        audit.consigner(acteur.id(), acteur.email(), ActionAudit.COMPTE_MODIFIE, "utilisateur", id, details);
        return Profil.de(utilisateur);
    }

    // ==================== GROUPES ====================

    @Transactional(readOnly = true)
    public List<GroupeResume> listerGroupes() {
        return groupes.findAll().stream()
                .map(GroupeResume::de)
                .sorted(Comparator.comparing(GroupeResume::nom))
                .toList();
    }

    @Transactional
    public GroupeResume creerGroupe(UtilisateurConnecte acteur, NomGroupe demande) {
        String nom = demande.nom().trim();
        verifierNomLibre(nom);
        Groupe groupe = groupes.save(new Groupe(nom));
        audit.consigner(acteur.id(), acteur.email(), ActionAudit.GROUPE_CREE, "groupe", groupe.getId(),
                Map.of("nom", nom));
        return GroupeResume.de(groupe);
    }

    @Transactional
    public GroupeResume renommerGroupe(UtilisateurConnecte acteur, UUID id, NomGroupe demande) {
        Groupe groupe = groupeModifiable(id);
        String nom = demande.nom().trim();
        if (!nom.equalsIgnoreCase(groupe.getNom())) {
            verifierNomLibre(nom);
        }
        String ancien = groupe.getNom();
        groupe.setNom(nom);
        audit.consigner(acteur.id(), acteur.email(), ActionAudit.GROUPE_RENOMME, "groupe", id,
                Map.of("avant", ancien, "apres", nom));
        return GroupeResume.de(groupe);
    }

    /** Supprime le groupe et, par cascade en base, toutes les appartenances qui s'y rattachent */
    @Transactional
    public void supprimerGroupe(UtilisateurConnecte acteur, UUID id) {
        Groupe groupe = groupeModifiable(id);
        groupes.delete(groupe);
        audit.consigner(acteur.id(), acteur.email(), ActionAudit.GROUPE_SUPPRIME, "groupe", id,
                Map.of("nom", groupe.getNom()));
    }

    // ==================== OUTILS ====================

    private Groupe groupeModifiable(UUID id) {
        Groupe groupe = groupes.findById(id).orElseThrow(ErreurMetier::introuvable);
        if (groupe.isSysteme()) {
            throw ErreurMetier.conflit("Ce groupe ne peut être ni renommé ni supprimé");
        }
        return groupe;
    }

    private void verifierNomLibre(String nom) {
        if (groupes.existsByNomIgnoreCase(nom)) {
            throw ErreurMetier.conflit("Un groupe porte déjà ce nom");
        }
    }

    /** Groupes demandés, auxquels s'ajoute toujours le groupe commun */
    private Set<Groupe> resoudre(Set<UUID> identifiants) {
        Set<UUID> demandes = identifiants == null ? Set.of() : identifiants;
        Set<Groupe> trouves = new HashSet<>(groupes.findAllById(demandes));
        if (trouves.size() != demandes.size()) {
            throw ErreurMetier.requeteInvalide("Groupe inconnu");
        }
        trouves.add(groupes.findByNom(Groupe.TOUS).orElseThrow());
        return trouves;
    }

    private String hacher(String motDePasse) {
        if (motDePasse.getBytes(StandardCharsets.UTF_8).length > OCTETS_MAX_MOT_DE_PASSE) {
            throw ErreurMetier.requeteInvalide("Mot de passe trop long");
        }
        return encodeur.encode(motDePasse);
    }

    /** Droits d'un compte, tels qu'ils sont consignés dans le journal d'audit */
    private static Map<String, Object> etat(Utilisateur utilisateur) {
        Map<String, Object> etat = new LinkedHashMap<>();
        etat.put("email", utilisateur.getEmail());
        etat.put("role", utilisateur.getRole().name());
        etat.put("actif", utilisateur.isActif());
        etat.put("groupes", utilisateur.getGroupes().stream().map(Groupe::getNom).sorted().toList());
        return etat;
    }
}

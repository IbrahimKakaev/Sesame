package fr.sesame.comptes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Compte utilisateur */
@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    @Id
    private UUID id;
    private String email;
    private String nom;
    private String motDePasse;
    @Enumerated(EnumType.STRING)
    private Role role;
    private boolean actif;
    private Instant creeLe;

    @ManyToMany
    @JoinTable(name = "utilisateur_groupe",
            joinColumns = @JoinColumn(name = "utilisateur_id"),
            inverseJoinColumns = @JoinColumn(name = "groupe_id"))
    private Set<Groupe> groupes = new HashSet<>();

    protected Utilisateur() {
    }

    /** @param empreinteMotDePasse empreinte bcrypt, jamais le mot de passe en clair */
    public Utilisateur(String email, String nom, String empreinteMotDePasse, Role role) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.nom = nom;
        this.motDePasse = empreinteMotDePasse;
        this.role = role;
        this.actif = true;
        this.creeLe = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String empreinteMotDePasse) {
        this.motDePasse = empreinteMotDePasse;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public Set<Groupe> getGroupes() {
        return groupes;
    }
}

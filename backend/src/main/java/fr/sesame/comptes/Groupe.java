package fr.sesame.comptes;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Groupe d'utilisateurs, qui représente un service de l'entreprise (RH, Finance...) */
@Entity
@Table(name = "groupe")
public class Groupe {

    /** Nom du groupe système auquel chaque compte appartient d'office */
    public static final String TOUS = "Tous";

    @Id
    private UUID id;
    private String nom;
    private boolean systeme;
    private Instant creeLe;

    protected Groupe() {
    }

    public Groupe(String nom) {
        this.id = UUID.randomUUID();
        this.nom = nom;
        this.creeLe = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public boolean isSysteme() {
        return systeme;
    }
}

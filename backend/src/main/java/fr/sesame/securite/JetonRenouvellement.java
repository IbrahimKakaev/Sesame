package fr.sesame.securite;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Jeton de renouvellement : seule son empreinte est conservée en base */
@Entity
@Table(name = "jeton_renouvellement")
public class JetonRenouvellement {

    @Id
    private UUID id;
    private UUID utilisateurId;
    private String empreinte;
    private UUID famille;
    private Instant expireLe;
    private boolean revoque;
    private Instant creeLe;

    protected JetonRenouvellement() {
    }

    JetonRenouvellement(UUID utilisateurId, String empreinte, UUID famille, Instant creeLe, Instant expireLe) {
        this.id = UUID.randomUUID();
        this.utilisateurId = utilisateurId;
        this.empreinte = empreinte;
        this.famille = famille;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public UUID getFamille() {
        return famille;
    }

    public Instant getExpireLe() {
        return expireLe;
    }

    public boolean isRevoque() {
        return revoque;
    }
}

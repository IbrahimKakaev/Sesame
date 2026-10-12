package fr.sesame.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Ligne du journal d'audit : qui a fait quoi, sur quoi, et quand */
@Entity
@Table(name = "journal_audit")
public class EntreeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant date;
    private UUID acteurId;
    private String acteurEmail;
    @Enumerated(EnumType.STRING)
    private ActionAudit action;
    private String cibleType;
    private String cibleId;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    protected EntreeAudit() {
    }

    EntreeAudit(Instant date, UUID acteurId, String acteurEmail, ActionAudit action,
            String cibleType, String cibleId, Map<String, Object> details) {
        this.date = date;
        this.acteurId = acteurId;
        this.acteurEmail = acteurEmail;
        this.action = action;
        this.cibleType = cibleType;
        this.cibleId = cibleId;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Instant getDate() {
        return date;
    }

    public UUID getActeurId() {
        return acteurId;
    }

    public String getActeurEmail() {
        return acteurEmail;
    }

    public ActionAudit getAction() {
        return action;
    }

    public String getCibleType() {
        return cibleType;
    }

    public String getCibleId() {
        return cibleId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}

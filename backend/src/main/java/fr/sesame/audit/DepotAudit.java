package fr.sesame.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotAudit extends JpaRepository<EntreeAudit, Long> {
}

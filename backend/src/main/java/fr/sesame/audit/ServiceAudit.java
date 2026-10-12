package fr.sesame.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Écrit le journal d'audit, dans la même transaction que l'action consignée */
@Service
public class ServiceAudit {

    private final DepotAudit depot;
    private final Clock horloge;

    public ServiceAudit(DepotAudit depot, Clock horloge) {
        this.depot = depot;
        this.horloge = horloge;
    }

    @Transactional
    public void consigner(UUID acteurId, String acteurEmail, ActionAudit action,
            String cibleType, Object cibleId, Map<String, Object> details) {
        depot.save(new EntreeAudit(horloge.instant(), acteurId, acteurEmail, action,
                cibleType, cibleId == null ? null : cibleId.toString(), details));
    }
}

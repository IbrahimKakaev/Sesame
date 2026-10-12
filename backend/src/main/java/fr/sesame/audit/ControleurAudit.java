package fr.sesame.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consultation du journal d'audit, réservée aux administrateurs */
@RestController
@RequestMapping("/api/admin/journal")
public class ControleurAudit {

    private static final int TAILLE_MAX = 100;

    private final DepotAudit depot;

    public ControleurAudit(DepotAudit depot) {
        this.depot = depot;
    }

    public record Ligne(Long id, Instant date, UUID acteurId, String acteurEmail, ActionAudit action,
            String cibleType, String cibleId, Map<String, Object> details) {
    }

    public record PageJournal(List<Ligne> lignes, long total) {
    }

    @GetMapping
    public PageJournal lister(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        var demande = PageRequest.of(Math.max(0, page), Math.clamp(taille, 1, TAILLE_MAX),
                Sort.by(Sort.Direction.DESC, "id"));
        var resultat = depot.findAll(demande);
        var lignes = resultat.getContent().stream()
                .map(e -> new Ligne(e.getId(), e.getDate(), e.getActeurId(), e.getActeurEmail(), e.getAction(),
                        e.getCibleType(), e.getCibleId(), e.getDetails()))
                .toList();
        return new PageJournal(lignes, resultat.getTotalElements());
    }
}

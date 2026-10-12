package fr.sesame.comptes;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotGroupes extends JpaRepository<Groupe, UUID> {

    Optional<Groupe> findByNom(String nom);

    boolean existsByNomIgnoreCase(String nom);
}

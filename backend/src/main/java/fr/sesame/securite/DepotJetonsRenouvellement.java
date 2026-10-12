package fr.sesame.securite;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DepotJetonsRenouvellement extends JpaRepository<JetonRenouvellement, UUID> {

    Optional<JetonRenouvellement> findByEmpreinte(String empreinte);

    /** Révoque le jeton s'il ne l'est pas déjà : 0 ligne modifiée signale un jeton rejoué */
    @Modifying
    @Query("update JetonRenouvellement j set j.revoque = true where j.id = :id and j.revoque = false")
    int revoquerSiValide(UUID id);

    @Modifying
    @Query("update JetonRenouvellement j set j.revoque = true where j.famille = :famille")
    int revoquerFamille(UUID famille);

    @Modifying
    @Query("update JetonRenouvellement j set j.revoque = true where j.utilisateurId = :utilisateurId")
    int revoquerPourUtilisateur(UUID utilisateurId);
}

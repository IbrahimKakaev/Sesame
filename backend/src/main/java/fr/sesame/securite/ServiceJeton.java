package fr.sesame.securite;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * ServiceJeton - Émet et vérifie les jetons d'accès (JWT signés HS256)
 *
 * Le jeton ne porte que l'identifiant du compte. Le rôle et les groupes n'y figurent
 * pas : ils sont relus en base à chaque requête, pour qu'un droit retiré le soit
 * dès la requête suivante.
 */
@Service
public class ServiceJeton {

    private final SecretKey cle;
    private final ProprietesSecurite proprietes;
    private final Clock horloge;

    public ServiceJeton(ProprietesSecurite proprietes, Clock horloge) {
        this.cle = Keys.hmacShaKeyFor(proprietes.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.proprietes = proprietes;
        this.horloge = horloge;
    }

    public String emettre(UUID utilisateurId) {
        return emettre(utilisateurId, horloge.instant());
    }

    /** @param emisLe instant d'émission, que les tests placent dans le passé */
    public String emettre(UUID utilisateurId, Instant emisLe) {
        return Jwts.builder()
                .subject(utilisateurId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(emisLe))
                .expiration(Date.from(emisLe.plus(proprietes.dureeJetonAcces())))
                .signWith(cle, Jwts.SIG.HS256)
                .compact();
    }

    /** Identifiant du compte si le jeton est authentique et encore valide, vide sinon */
    public Optional<UUID> lire(String jeton) {
        try {
            // parseSignedClaims refuse les jetons non signés (alg « none »)
            String sujet = Jwts.parser()
                    .verifyWith(cle)
                    .clock(() -> Date.from(horloge.instant()))
                    .build()
                    .parseSignedClaims(jeton)
                    .getPayload()
                    .getSubject();
            return Optional.of(UUID.fromString(sujet));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public long dureeEnSecondes() {
        return proprietes.dureeJetonAcces().toSeconds();
    }
}

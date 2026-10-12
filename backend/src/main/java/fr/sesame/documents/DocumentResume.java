package fr.sesame.documents;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ce que l'API expose d'un document : ses métadonnées, jamais son contenu */
public record DocumentResume(UUID id, String titre, String nomFichier, long tailleOctets, Integer nombrePages,
        EtatDocument etat, String motifEchec, Instant deposeLe, List<String> groupes) {
}

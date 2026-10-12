package fr.sesame.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.sesame.comptes.DepotGroupes;
import fr.sesame.comptes.DepotUtilisateurs;
import fr.sesame.comptes.Groupe;
import fr.sesame.comptes.Role;
import fr.sesame.comptes.ServiceComptes;
import fr.sesame.comptes.ServiceComptes.CreationCompte;
import fr.sesame.documents.ServiceDepot;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ChargementDemo - Données de démonstration, chargées au premier démarrage
 *
 * Actif seulement si SESAME_DEMO=true. Crée les groupes, les quatre comptes et les
 * dix documents de l'entreprise fictive Hélianthe Ingénierie (resources/demo). Les
 * documents passent par le dépôt normal : mêmes contrôles et même indexation
 * qu'un envoi par l'interface.
 *
 * Le mot de passe des comptes de démonstration est public (demo.json) : ce mode
 * ne doit jamais être activé sur une installation qui contient de vrais documents.
 */
@Component
@ConditionalOnProperty(name = "sesame.demo.charger", havingValue = "true")
public class ChargementDemo implements ApplicationRunner {

    private static final Logger journal = LoggerFactory.getLogger(ChargementDemo.class);

    public record Jeu(String motDePasse, List<String> groupes, List<Compte> comptes, List<Document> documents) {
    }

    public record Compte(String email, String nom, Role role, List<String> groupes) {
    }

    public record Document(String fichier, String titre, List<String> groupes) {
    }

    private final ServiceComptes comptes;
    private final ServiceDepot depot;
    private final DepotGroupes groupes;
    private final DepotUtilisateurs utilisateurs;
    private final ObjectMapper json;

    public ChargementDemo(ServiceComptes comptes, ServiceDepot depot, DepotGroupes groupes,
            DepotUtilisateurs utilisateurs, ObjectMapper json) {
        this.comptes = comptes;
        this.depot = depot;
        this.groupes = groupes;
        this.utilisateurs = utilisateurs;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Jeu jeu = lire("demo/demo.json", flux -> json.readValue(flux, Jeu.class));
        // Premier démarrage seulement : si un compte de démonstration existe, tout est déjà chargé
        if (jeu.comptes().stream().anyMatch(compte -> utilisateurs.existsByEmail(compte.email()))) {
            return;
        }
        Map<String, UUID> idDeGroupe = new HashMap<>();
        idDeGroupe.put(Groupe.TOUS, groupes.findByNom(Groupe.TOUS).orElseThrow().getId());
        for (String nom : jeu.groupes()) {
            idDeGroupe.put(nom, groupes.findByNom(nom).orElseGet(() -> groupes.save(new Groupe(nom))).getId());
        }
        for (Compte compte : jeu.comptes()) {
            comptes.creer(null, new CreationCompte(compte.email(), compte.nom(), jeu.motDePasse(), compte.role(),
                    identifiants(compte.groupes(), idDeGroupe)));
        }
        for (Document document : jeu.documents()) {
            byte[] contenu = lire("demo/documents/" + document.fichier(), InputStream::readAllBytes);
            depot.deposer(null, contenu, document.fichier(), document.titre(),
                    identifiants(document.groupes(), idDeGroupe));
        }
        journal.warn("Données de démonstration chargées : {} comptes au mot de passe public, {} documents",
                jeu.comptes().size(), jeu.documents().size());
    }

    private static Set<UUID> identifiants(List<String> noms, Map<String, UUID> idDeGroupe) {
        return noms.stream().map(idDeGroupe::get).collect(Collectors.toSet());
    }

    private interface Lecture<T> {
        T lire(InputStream flux) throws IOException;
    }

    private static <T> T lire(String chemin, Lecture<T> lecture) {
        try (InputStream flux = new ClassPathResource(chemin).getInputStream()) {
            return lecture.lire(flux);
        } catch (IOException e) {
            throw new UncheckedIOException("Ressource de démonstration illisible : " + chemin, e);
        }
    }
}

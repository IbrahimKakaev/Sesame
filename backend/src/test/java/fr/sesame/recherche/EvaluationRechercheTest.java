package fr.sesame.recherche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sesame.SocleIntegration;
import fr.sesame.comptes.Role;
import fr.sesame.demo.ChargementDemo;
import fr.sesame.recherche.ServiceRecherche.Mode;
import fr.sesame.recherche.ServiceRecherche.PassageTrouve;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;

/**
 * Évaluation de la recherche avec le vrai modèle d'embeddings
 *
 * Vingt-quatre questions sur les documents de démonstration, chacune avec le document
 * où se trouve la réponse. Une source est « retrouvée » quand ce document figure parmi
 * les passages qui seraient transmis au LLM. Les deux modes de recherche sont mesurés ;
 * le résultat est écrit dans target/evaluation-recherche.json, d'où la CI tire un badge.
 */
@EnabledIf("fr.sesame.embeddings.MoteurEmbeddingsOnnxTest#modeleDisponible")
@TestPropertySource(properties = {"sesame.embeddings.moteur=onnx", "sesame.demo.charger=true",
        "sesame.recherche.seuil-similarite=${sesame.recherche.seuil-de-production}"})
class EvaluationRechercheTest extends SocleIntegration {

    // Nombre de passages transmis au LLM pour une question
    private static final int PASSAGES = 5;

    @Autowired
    private ChargementDemo chargement;
    @Autowired
    private ServiceRecherche recherche;
    @Value("${sesame.recherche.seuil-similarite}")
    private double seuil;

    private UUID lecteur;
    private JsonNode jeu;

    @BeforeEach
    void charger() throws Exception {
        chargement.run(null);
        await().atMost(Duration.ofMinutes(2)).until(() -> sql.queryForObject(
                "SELECT count(*) FROM document WHERE etat <> 'INDEXE'", Integer.class) == 0);
        // Un compte membre de tous les groupes : l'évaluation mesure la recherche, pas les droits
        lecteur = compte("evaluation@exemple.test", Role.LECTEUR, "RH", "Finance", "Direction");
        jeu = json.readTree(new ClassPathResource("evaluation/questions.json").getInputStream());
    }

    private record Mesure(int questions, int retrouvees, int enTete, List<String> manquees) {

        int taux() {
            return Math.round(100f * retrouvees / questions);
        }
    }

    private Mesure mesurer(Mode mode) {
        int retrouvees = 0;
        int enTete = 0;
        List<String> manquees = new ArrayList<>();
        for (JsonNode cas : jeu.get("questions")) {
            String attendu = cas.get("document").asText();
            List<PassageTrouve> passages = recherche.chercher(lecteur, cas.get("question").asText(), PASSAGES, mode);
            if (passages.stream().anyMatch(passage -> passage.titre().equals(attendu))) {
                retrouvees++;
            } else {
                manquees.add(cas.get("question").asText());
            }
            if (!passages.isEmpty() && passages.get(0).titre().equals(attendu)) {
                enTete++;
            }
        }
        return new Mesure(jeu.get("questions").size(), retrouvees, enTete, manquees);
    }

    @Test
    @DisplayName("la bonne source est retrouvée pour au moins 90 % des questions, et l'hybride ne fait pas moins bien que les vecteurs seuls")
    void tauxDeBonnesSources() throws Exception {
        Mesure hybride = mesurer(Mode.HYBRIDE);
        Mesure vecteurs = mesurer(Mode.VECTEURS);

        Map<String, Object> rapport = new LinkedHashMap<>();
        rapport.put("questions", hybride.questions());
        rapport.put("passagesTransmis", PASSAGES);
        for (var entree : Map.of("hybride", hybride, "vecteurs", vecteurs).entrySet()) {
            Map<String, Object> mesure = new LinkedHashMap<>();
            mesure.put("sourcesRetrouvees", entree.getValue().retrouvees());
            mesure.put("taux", entree.getValue().taux());
            mesure.put("bonneSourceEnTete", entree.getValue().enTete());
            mesure.put("manquees", entree.getValue().manquees());
            rapport.put(entree.getKey(), mesure);
        }
        Files.createDirectories(Path.of("target"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/evaluation-recherche.json").toFile(), rapport);
        System.out.println("ÉVALUATION " + json.writeValueAsString(rapport));

        assertThat(hybride.taux()).as("questions manquées : %s", hybride.manquees()).isGreaterThanOrEqualTo(90);
        assertThat(hybride.retrouvees()).isGreaterThanOrEqualTo(vecteurs.retrouvees());
    }

    @Test
    @DisplayName("le seuil de pertinence laisse passer toutes les questions du jeu et écarte la plupart des questions hors sujet")
    void seuilDePertinence() {
        List<Double> pertinentes = new ArrayList<>();
        for (JsonNode cas : jeu.get("questions")) {
            pertinentes.add(meilleure(cas.get("question").asText()));
        }
        List<Double> horsSujet = new ArrayList<>();
        for (JsonNode question : jeu.get("horsSujet")) {
            horsSujet.add(meilleure(question.asText()));
        }
        System.out.printf("SEUIL pertinentes min=%.3f | hors sujet=%s | seuil=%.2f%n",
                pertinentes.stream().mapToDouble(d -> d).min().orElse(0), horsSujet, seuil);

        // Le seuil ne doit jamais faire taire une question légitime
        assertThat(pertinentes).allMatch(similarite -> similarite >= seuil);
        // Il écarte la plupart des questions hors sujet avant tout appel au LLM. Celles qui
        // passent ne coûtent qu'un appel : le modèle répond alors lui-même qu'il ne sait pas.
        assertThat(horsSujet.stream().filter(similarite -> similarite < seuil).count())
                .isGreaterThanOrEqualTo(horsSujet.size() - 1);
    }

    private double meilleure(String question) {
        return recherche.chercher(lecteur, question, PASSAGES, Mode.HYBRIDE).stream()
                .mapToDouble(PassageTrouve::similarite).max().orElse(0);
    }
}

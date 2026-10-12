package fr.sesame;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.sesame.comptes.Role;
import fr.sesame.securite.LimiteurDebit;
import fr.sesame.securite.ServiceJeton;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Socle des tests d'intégration : l'application complète, branchée sur une vraie
 * base PostgreSQL (image pgvector) lancée par Testcontainers et partagée par
 * toutes les classes de test. Les tables sont vidées avant chaque test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddingsDeTest.class)
// Embeddings déterministes (voir EmbeddingsDeTest), avec un seuil de pertinence à leur échelle
@TestPropertySource(properties = {"sesame.embeddings.moteur=test", "sesame.recherche.seuil-similarite=0.2",
        "sesame.llm.cle-api=cle-de-test", "sesame.llm.tentatives=0"})
public abstract class SocleIntegration {

    protected static final String MOT_DE_PASSE = "mot-de-passe-de-test";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    /** Faux serveur de l'API Claude : il capture ce que l'application envoie au modèle */
    protected static final FauxClaude CLAUDE = new FauxClaude();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registre.add("spring.datasource.username", POSTGRES::getUsername);
        registre.add("spring.datasource.password", POSTGRES::getPassword);
        // Secret tiré au hasard à chaque lancement : aucun secret dans le dépôt, même pour les tests
        byte[] secret = new byte[48];
        ThreadLocalRandom.current().nextBytes(secret);
        String secretJwt = Base64.getEncoder().encodeToString(secret);
        registre.add("sesame.securite.jwt-secret", () -> secretJwt);
        registre.add("sesame.llm.url-de-base", CLAUDE::url);
        // Coût bcrypt minimal pour des tests rapides
        registre.add("sesame.securite.cout-bcrypt", () -> 4);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate sql;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected ServiceJeton serviceJeton;
    @Autowired
    protected PasswordEncoder encodeur;
    @Autowired
    private List<LimiteurDebit> limiteurs;

    @BeforeEach
    void repartirDeZero() {
        // Une indexation lancée par le test précédent doit être terminée avant de vider les tables
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(25)).until(() -> sql.queryForObject(
                "SELECT count(*) FROM document WHERE etat IN ('EN_ATTENTE', 'EN_COURS')", Integer.class) == 0);
        sql.execute("TRUNCATE utilisateur, jeton_renouvellement, journal_audit, document RESTART IDENTITY CASCADE");
        sql.execute("DELETE FROM groupe WHERE NOT systeme");
        limiteurs.forEach(LimiteurDebit::reinitialiser);
        CLAUDE.reinitialiser();
    }

    // ==================== FABRIQUE ====================

    /** Crée un compte actif, membre du groupe Tous et des groupes cités (créés au besoin) */
    protected UUID compte(String email, Role role, String... groupes) {
        UUID id = UUID.randomUUID();
        sql.update("INSERT INTO utilisateur (id, email, nom, mot_de_passe, role) VALUES (?, ?, ?, ?, ?)",
                id, email, email, encodeur.encode(MOT_DE_PASSE), role.name());
        inscrire(id, "Tous");
        for (String groupe : groupes) {
            inscrire(id, groupe);
        }
        return id;
    }

    protected UUID groupe(String nom) {
        sql.update("INSERT INTO groupe (nom) VALUES (?) ON CONFLICT (nom) DO NOTHING", nom);
        return sql.queryForObject("SELECT id FROM groupe WHERE nom = ?", UUID.class, nom);
    }

    protected void inscrire(UUID utilisateur, String nomGroupe) {
        sql.update("INSERT INTO utilisateur_groupe (utilisateur_id, groupe_id) VALUES (?, ?)",
                utilisateur, groupe(nomGroupe));
    }

    /** En-tête Authorization d'un jeton d'accès valide pour ce compte */
    protected String porteur(UUID utilisateur) {
        return "Bearer " + serviceJeton.emettre(utilisateur);
    }

    /**
     * Dépose un PDF par l'API au nom du compte, puis attend la fin de son indexation.
     *
     * @return l'identifiant du document
     */
    protected UUID deposer(UUID auteur, String titre, byte[] pdf, String... groupes) throws Exception {
        var requete = multipart("/api/documents")
                .file(new MockMultipartFile("fichier", titre + ".pdf", "application/pdf", pdf))
                .param("titre", titre)
                .header("Authorization", porteur(auteur));
        for (String groupe : groupes) {
            requete.param("groupes", groupe(groupe).toString());
        }
        UUID id = UUID.fromString(corps(mvc.perform(requete).andExpect(status().isCreated()).andReturn())
                .get("id").asText());
        attendreIndexation(id);
        return id;
    }

    /** Attend que l'indexation en arrière-plan soit terminée, et rend l'état final */
    protected String attendreIndexation(UUID document) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(25))
                .until(() -> etat(document), etat -> !etat.equals("EN_ATTENTE") && !etat.equals("EN_COURS"));
    }

    protected String etat(UUID document) {
        return sql.queryForObject("SELECT etat FROM document WHERE id = ?", String.class, document);
    }

    /** Événement SSE reçu en réponse à une question */
    public record Evenement(String nom, JsonNode donnees) {
    }

    /** Réponse à une question : statut HTTP et événements du flux */
    public record Flux(int statut, String corps, List<Evenement> evenements) {

        public List<JsonNode> de(String nom) {
            return evenements.stream().filter(e -> e.nom().equals(nom)).map(Evenement::donnees).toList();
        }

        public JsonNode fin() {
            return de("fin").get(0);
        }

        /** Texte reçu au fil de l'eau, avant le texte définitif de l'événement de fin */
        public String texteDiffuse() {
            StringBuilder texte = new StringBuilder();
            de("texte").forEach(e -> texte.append(e.get("texte").asText()));
            return texte.toString();
        }
    }

    protected Flux poser(String jeton, String question) throws Exception {
        return poser(jeton, Map.of("question", question));
    }

    protected Flux poser(String jeton, Map<String, Object> demande) throws Exception {
        var reponse = mvc.perform(post("/api/questions").header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(demande)))
                .andReturn().getResponse();
        String corps = reponse.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<Evenement> evenements = new java.util.ArrayList<>();
        if (reponse.getStatus() == 200) {
            for (String bloc : corps.split("\n\n")) {
                String[] lignes = bloc.split("\n", 2);
                evenements.add(new Evenement(lignes[0].substring("event: ".length()),
                        json.readTree(lignes[1].substring("data: ".length()))));
            }
        }
        return new Flux(reponse.getStatus(), corps, evenements);
    }

    protected JsonNode corps(MvcResult resultat) throws Exception {
        return json.readTree(resultat.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected List<String> actionsAudit() {
        return sql.queryForList("SELECT action FROM journal_audit ORDER BY id", String.class);
    }
}

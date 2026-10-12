package fr.sesame.etancheite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sesame.SocleIntegration;
import fr.sesame.FauxClaude;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Outils communs aux tests d'étanchéité : pour un compte et un document, interroger
 * chacune des routes qui peuvent exposer le document (liste, détail, fichier, extrait,
 * recherche, question) et dire s'il y apparaît.
 */
abstract class SocleEtancheite extends SocleIntegration {

    /** Routes par lesquelles un document peut être atteint */
    enum Route { LISTE, DETAIL, FICHIER, EXTRAIT, RECHERCHE, QUESTION }

    /** Réponse HTTP réduite à ce que les tests comparent */
    record Reponse(int statut, String corps) {
    }

    protected Reponse appeler(String jeton, String chemin) throws Exception {
        MockHttpServletResponse reponse = mvc.perform(get(chemin).header("Authorization", jeton))
                .andReturn().getResponse();
        return new Reponse(reponse.getStatus(), reponse.getContentAsString(StandardCharsets.UTF_8));
    }

    protected Reponse chercher(String jeton, String question) throws Exception {
        MockHttpServletResponse reponse = mvc.perform(post("/api/recherche").header("Authorization", jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", question, "limite", 10))))
                .andReturn().getResponse();
        return new Reponse(reponse.getStatus(), reponse.getContentAsString(StandardCharsets.UTF_8));
    }

    // Texte de chaque passage, retenu pour le reconnaître même après la suppression du document
    private final Map<UUID, String> textes = new HashMap<>();

    protected UUID passageDe(UUID document) {
        UUID passage = sql.queryForObject("SELECT id FROM passage WHERE document_id = ? ORDER BY position LIMIT 1",
                UUID.class, document);
        textes.put(passage, sql.queryForObject("SELECT contenu FROM passage WHERE id = ?", String.class, passage));
        return passage;
    }

    /**
     * Pose la question et dit si le passage a été transmis au modèle : la preuve est
     * cherchée dans le corps de la requête HTTP reçue par le faux serveur Claude.
     */
    private boolean transmisAuModele(String jeton, UUID document, UUID passage, String question) throws Exception {
        int avant = CLAUDE.requetes().size();
        Flux flux = poser(jeton, question);
        if (flux.statut() != 200) {
            throw new AssertionError("Réponse inattendue : " + flux.statut() + " " + flux.corps());
        }
        boolean transmis = CLAUDE.requetes().size() > avant
                && FauxClaude.documents(CLAUDE.derniereRequete()).contains(textes.get(passage));
        boolean cite = flux.de("citation").stream()
                .anyMatch(citation -> citation.get("documentId").asText().equals(document.toString()));
        if (cite && !transmis) {
            throw new AssertionError("Citation d'un document qui n'a pas été transmis au modèle");
        }
        return transmis;
    }

    /**
     * Le document est-il atteint par cette route ? Vérifie au passage qu'un refus est
     * un 404 strictement identique à celui d'un identifiant qui n'existe pas.
     *
     * @param question question dont la réponse se trouve dans le document
     */
    protected boolean atteint(Route route, String jeton, UUID document, UUID passage, String question)
            throws Exception {
        return switch (route) {
            case LISTE -> contient(appeler(jeton, "/api/documents"), "id", document);
            case DETAIL -> ouvert(jeton, "/api/documents/" + document, "/api/documents/" + UUID.randomUUID());
            case FICHIER -> ouvert(jeton, "/api/documents/" + document + "/fichier",
                    "/api/documents/" + UUID.randomUUID() + "/fichier");
            case EXTRAIT -> ouvert(jeton, "/api/documents/" + document + "/passages/" + passage,
                    "/api/documents/" + UUID.randomUUID() + "/passages/" + UUID.randomUUID());
            case RECHERCHE -> contient(chercher(jeton, question), "documentId", document);
            case QUESTION -> transmisAuModele(jeton, document, passage, question);
        };
    }

    /** Toutes les routes d'un coup : route -> le document y est-il atteint */
    protected Map<Route, Boolean> acces(String jeton, UUID document, UUID passage, String question) throws Exception {
        Map<Route, Boolean> acces = new LinkedHashMap<>();
        for (Route route : Route.values()) {
            acces.put(route, atteint(route, jeton, document, passage, question));
        }
        return acces;
    }

    private boolean ouvert(String jeton, String chemin, String cheminInexistant) throws Exception {
        Reponse reponse = appeler(jeton, chemin);
        if (reponse.statut() == 200) {
            return true;
        }
        Reponse inexistant = appeler(jeton, cheminInexistant);
        if (reponse.statut() != 404 || !reponse.equals(inexistant)) {
            throw new AssertionError("Un refus doit être indiscernable d'un document inexistant : " + chemin
                    + " a répondu " + reponse + " au lieu de " + inexistant);
        }
        return false;
    }

    private boolean contient(Reponse reponse, String champ, UUID document) throws Exception {
        if (reponse.statut() != 200) {
            throw new AssertionError("Réponse inattendue : " + reponse);
        }
        JsonNode corps = json.readTree(reponse.corps());
        return corps.findValuesAsText(champ).contains(document.toString());
    }
}

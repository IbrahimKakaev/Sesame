package fr.sesame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Faux serveur de l'API Claude pour les tests
 *
 * L'application lui parle avec le vrai SDK, par HTTP : il enregistre le corps exact de
 * chaque requête reçue, ce qui permet de vérifier octet par octet ce qui est transmis
 * au modèle. Il répond par un flux SSE au format de l'API, écrit par un scénario : par
 * défaut un modèle honnête qui cite le premier document, ou au choix un modèle
 * manipulé qui invente des citations.
 */
public final class FauxClaude {

    /** Fragment de réponse joué par le faux modèle */
    public sealed interface Fragment {
    }

    public record Texte(String texte) implements Fragment {
    }

    /** Citation du document de rang donné, avec le texte que le modèle prétend en tirer */
    public record Cite(int document, String texteCite) implements Fragment {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer serveur;
    private final List<String> requetes = new CopyOnWriteArrayList<>();
    private final List<String> cles = new CopyOnWriteArrayList<>();
    private volatile Function<JsonNode, List<Fragment>> scenario;
    private volatile String raisonDeFin;
    private volatile int statut;

    public FauxClaude() {
        try {
            serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serveur.createContext("/", this::repondre);
        serveur.start();
        reinitialiser();
    }

    public String url() {
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    /** Revient au modèle honnête et oublie les requêtes reçues */
    public void reinitialiser() {
        requetes.clear();
        cles.clear();
        raisonDeFin = "end_turn";
        statut = 200;
        scenario = requete -> List.of(new Texte("D'après le document, la réponse est la suivante."),
                new Cite(0, documents(requete).get(0)));
    }

    public void jouer(Function<JsonNode, List<Fragment>> scenario) {
        this.scenario = scenario;
    }

    public void jouer(Fragment... fragments) {
        this.scenario = requete -> List.of(fragments);
    }

    public void finirPar(String raison) {
        this.raisonDeFin = raison;
    }

    public void repondreEnErreur(int statut) {
        this.statut = statut;
    }

    /** Corps exacts des requêtes reçues, dans l'ordre */
    public List<String> requetes() {
        return List.copyOf(requetes);
    }

    public String derniereRequete() {
        return requetes.get(requetes.size() - 1);
    }

    public List<String> clesRecues() {
        return List.copyOf(cles);
    }

    /** Texte de chaque document joint à une requête, dans l'ordre */
    public static List<String> documents(JsonNode requete) {
        List<String> textes = new ArrayList<>();
        for (JsonNode message : requete.get("messages")) {
            for (JsonNode bloc : message.get("content")) {
                if ("document".equals(bloc.path("type").asText())) {
                    StringBuilder texte = new StringBuilder();
                    bloc.at("/source/content").forEach(partie -> texte.append(partie.path("text").asText()));
                    textes.add(texte.toString());
                }
            }
        }
        return textes;
    }

    public static List<String> documents(String requete) {
        try {
            return documents(JSON.readTree(requete));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void repondre(HttpExchange echange) throws IOException {
        String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requetes.add(corps);
        cles.add(String.valueOf(echange.getRequestHeaders().getFirst("x-api-key")));
        if (statut != 200) {
            byte[] erreur = "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"panne simulée\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/json");
            echange.sendResponseHeaders(statut, erreur.length);
            echange.getResponseBody().write(erreur);
            echange.close();
            return;
        }
        echange.getResponseHeaders().add("Content-Type", "text/event-stream");
        echange.sendResponseHeaders(200, 0);
        try (OutputStream sortie = echange.getResponseBody()) {
            evenement(sortie, "message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_test\","
                    + "\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-haiku-5-5\",\"content\":[],"
                    + "\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}}");
            evenement(sortie, "content_block_start", "{\"type\":\"content_block_start\",\"index\":0,"
                    + "\"content_block\":{\"type\":\"text\",\"text\":\"\",\"citations\":[]}}");
            for (Fragment fragment : scenario.apply(JSON.readTree(corps))) {
                ObjectNode delta = JSON.createObjectNode();
                if (fragment instanceof Texte texte) {
                    delta.put("type", "text_delta").put("text", texte.texte());
                } else if (fragment instanceof Cite cite) {
                    delta.put("type", "citations_delta");
                    delta.putObject("citation").put("type", "content_block_location")
                            .put("cited_text", cite.texteCite()).put("document_index", cite.document())
                            .put("document_title", "titre").put("start_block_index", 0).put("end_block_index", 1);
                }
                ObjectNode bloc = JSON.createObjectNode().put("type", "content_block_delta").put("index", 0);
                bloc.set("delta", delta);
                evenement(sortie, "content_block_delta", bloc.toString());
            }
            evenement(sortie, "content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}");
            evenement(sortie, "message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\""
                    + raisonDeFin + "\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":5}}");
            evenement(sortie, "message_stop", "{\"type\":\"message_stop\"}");
        }
    }

    private static void evenement(OutputStream sortie, String nom, String donnees) throws IOException {
        sortie.write(("event: " + nom + "\ndata: " + donnees + "\n\n").getBytes(StandardCharsets.UTF_8));
        sortie.flush();
    }
}

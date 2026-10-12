package fr.sesame.documents;

import static fr.sesame.FabriquePdf.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import fr.sesame.FabriquePdf;
import fr.sesame.SocleIntegration;
import fr.sesame.comptes.Role;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Dépôt d'un PDF, indexation en arrière-plan, fichiers hostiles et gestion des droits */
class DepotDeDocumentsTest extends SocleIntegration {

    private static final String PAGE_1 = "Le télétravail est ouvert à tous les salariés après la période d'essai. "
            + "Il est limité à deux jours par semaine.";
    private static final String PAGE_2 = "Une indemnité forfaitaire de 2,50 euros est versée par jour télétravaillé.";

    @Autowired
    private Indexeur indexeur;

    private ResultActions envoyer(UUID auteur, MockMultipartFile fichier, String... groupes) throws Exception {
        var requete = multipart("/api/documents").file(fichier).header("Authorization", porteur(auteur));
        for (String groupe : groupes) {
            requete.param("groupes", groupe);
        }
        return mvc.perform(requete);
    }

    private static MockMultipartFile fichier(String nom, String type, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, type, contenu);
    }

    private int nombreDeDocuments() {
        return sql.queryForObject("SELECT count(*) FROM document", Integer.class);
    }

    // ==================== DÉPÔT ET INDEXATION ====================

    @Test
    @DisplayName("un PDF déposé est indexé en arrière-plan, passage par passage, avec ses numéros de page")
    void depotPuisIndexation() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");

        JsonNode depose = corps(envoyer(karim, fichier("teletravail.pdf", "application/pdf", pdf(PAGE_1, PAGE_2)),
                groupe("RH").toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titre").value("teletravail"))
                .andExpect(jsonPath("$.groupes").value(contains("RH")))
                .andReturn());
        UUID id = UUID.fromString(depose.get("id").asText());
        // La réponse arrive avant la fin du traitement
        assertThat(depose.get("etat").asText()).isIn("EN_ATTENTE", "EN_COURS", "INDEXE");

        assertThat(attendreIndexation(id)).isEqualTo("INDEXE");
        mvc.perform(get("/api/documents/" + id).header("Authorization", porteur(karim)))
                .andExpect(jsonPath("$.etat").value("INDEXE"))
                .andExpect(jsonPath("$.nombrePages").value(2));
        List<Map<String, Object>> passages = sql.queryForList(
                "SELECT page, contenu, vector_dims(embedding) AS dimensions FROM passage WHERE document_id = ? ORDER BY position",
                id);
        assertThat(passages).hasSize(2);
        assertThat(passages.get(0)).containsEntry("page", 1).containsEntry("dimensions", 384);
        assertThat((String) passages.get(0).get("contenu")).contains("deux jours par semaine");
        assertThat(passages.get(1)).containsEntry("page", 2);
        assertThat((String) passages.get(1).get("contenu")).contains("2,50 euros");
        assertThat(actionsAudit()).containsExactly("DOCUMENT_DEPOSE");
    }

    @Test
    @DisplayName("un lecteur ne peut pas déposer de document")
    void lecteurRefuse() throws Exception {
        UUID lea = compte("lea@exemple.test", Role.LECTEUR, "RH");

        envoyer(lea, fichier("note.pdf", "application/pdf", pdf(PAGE_1)), groupe("RH").toString())
                .andExpect(status().isForbidden());
        assertThat(nombreDeDocuments()).isZero();
    }

    @Test
    @DisplayName("un contributeur ne dépose que dans ses groupes ; groupe d'un autre service et groupe inconnu donnent le même refus")
    void groupesDuContributeur() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        MockMultipartFile note = fichier("note.pdf", "application/pdf", pdf(PAGE_1));

        String autreService = envoyer(karim, note, groupe("Direction").toString())
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String inconnu = envoyer(karim, note, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        envoyer(karim, note, groupe("RH").toString(), groupe("Direction").toString())
                .andExpect(status().isBadRequest());
        envoyer(karim, note).andExpect(status().isBadRequest());

        assertThat(autreService).isEqualTo(inconnu);
        assertThat(nombreDeDocuments()).isZero();
    }

    @Test
    @DisplayName("un administrateur peut déposer dans n'importe quel groupe existant")
    void depotParAdministrateur() throws Exception {
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        MockMultipartFile note = fichier("note.pdf", "application/pdf", pdf(PAGE_1));

        envoyer(admin, note, groupe("Finance").toString()).andExpect(status().isCreated());
        envoyer(admin, note, UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }

    // ==================== FICHIERS HOSTILES ====================

    static Stream<Arguments> fauxPdf() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] zip = {'P', 'K', 0x03, 0x04, 0, 0, 0, 0};
        byte[] executable = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        return Stream.of(
                Arguments.of("une image renommée en .pdf", png),
                Arguments.of("une archive zip renommée en .pdf", zip),
                Arguments.of("un exécutable renommé en .pdf", executable),
                Arguments.of("une page HTML", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("un fichier vide", new byte[0]),
                Arguments.of("une signature PDF tronquée", "%PDF".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("une signature PDF qui n'est pas au début", " %PDF-1.7".getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest(name = "{0} est refusée, même annoncée comme application/pdf")
    @MethodSource("fauxPdf")
    void typeVerifieSurLeContenu(String cas, byte[] contenu) throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");

        envoyer(karim, fichier("rapport.pdf", "application/pdf", contenu), groupe("RH").toString())
                .andExpect(status().isBadRequest());

        assertThat(nombreDeDocuments()).isZero();
    }

    @Test
    @DisplayName("un vrai PDF est accepté même si son nom et son type annoncé mentent")
    void vraiPdfMalAnnonce() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");

        envoyer(karim, fichier("note.txt", "text/plain", pdf(PAGE_1)), groupe("RH").toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomFichier").value("note.txt.pdf"));
    }

    @Test
    @DisplayName("un fichier au-delà de 10 Mo est refusé en 413")
    void fichierTropVolumineux() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        byte[] enorme = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, enorme, 0, 8);

        envoyer(karim, fichier("enorme.pdf", "application/pdf", enorme), groupe("RH").toString())
                .andExpect(status().isPayloadTooLarge());
        assertThat(nombreDeDocuments()).isZero();
    }

    static Stream<Arguments> pdfInexploitables() {
        byte[] tronque = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nceci n'est pas un PDF".getBytes(StandardCharsets.UTF_8);
        return Stream.of(
                Arguments.of("un PDF corrompu", tronque, "Le PDF est illisible"),
                Arguments.of("un PDF protégé par mot de passe", FabriquePdf.pdfChiffre(PAGE_1), "Le PDF est protégé"),
                Arguments.of("un PDF de plus de 300 pages", FabriquePdf.pdfSansTexte(301), "Le PDF dépasse 300 pages"),
                Arguments.of("un PDF sans texte", FabriquePdf.pdfSansTexte(2),
                        "Le PDF ne contient aucun texte exploitable (document scanné ?)"));
    }

    @ParameterizedTest(name = "{0} passe en échec, sans passage indexé")
    @MethodSource("pdfInexploitables")
    void indexationEnEchec(String cas, byte[] contenu, String motif) throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");

        UUID id = UUID.fromString(corps(envoyer(karim, fichier("piege.pdf", "application/pdf", contenu),
                groupe("RH").toString()).andExpect(status().isCreated()).andReturn()).get("id").asText());

        assertThat(attendreIndexation(id)).isEqualTo("EN_ECHEC");
        mvc.perform(get("/api/documents/" + id).header("Authorization", porteur(karim)))
                .andExpect(jsonPath("$.etat").value("EN_ECHEC"))
                .andExpect(jsonPath("$.motifEchec").value(motif));
        assertThat(sql.queryForObject("SELECT count(*) FROM passage", Integer.class)).isZero();
    }

    static Stream<Arguments> nomsHostiles() {
        return Stream.of(
                Arguments.of("../../../etc/passwd", "passwd.pdf"),
                Arguments.of("..\\..\\windows\\system32\\config.pdf", "config.pdf"),
                Arguments.of("rapport\".pdf\r\nX-Injecte: oui", "rapport_.pdf__X-Injecte_ oui.pdf"),
                Arguments.of("<img src=x onerror=alert(1)>.pdf", "_img src_x onerror_alert(1)_.pdf"),
                Arguments.of("facture.pdf.exe", "facture.pdf.exe.pdf"),
                Arguments.of("....pdf", "document.pdf"),
                Arguments.of("", "document.pdf"),
                Arguments.of("Synthèse été 2026.PDF", "Synthèse été 2026.pdf"),
                Arguments.of("a".repeat(300) + ".pdf", "a".repeat(96) + ".pdf"));
    }

    @ParameterizedTest(name = "le nom « {0} » est assaini")
    @MethodSource("nomsHostiles")
    void nomDeFichierAssaini(String envoye, String attendu) throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");

        UUID id = UUID.fromString(corps(envoyer(karim, fichier(envoye, "application/pdf", pdf(PAGE_1)),
                groupe("RH").toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomFichier").value(attendu))
                .andReturn()).get("id").asText());

        // L'en-tête de téléchargement ne contient ni saut de ligne ni guillemet venu du nom
        String entete = mvc.perform(get("/api/documents/" + id + "/fichier").header("Authorization", porteur(karim)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Disposition");
        assertThat(entete).startsWith("attachment;").doesNotContain("\r").doesNotContain("\n").doesNotContain("..");
    }

    @Test
    @DisplayName("le titre fourni est nettoyé de ses caractères de contrôle")
    void titreNettoye() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");

        mvc.perform(multipart("/api/documents")
                        .file(fichier("note.pdf", "application/pdf", pdf(PAGE_1)))
                        .param("titre", "  Politique\r\nde\u0000 télétravail‮  ")
                        .param("groupes", groupe("RH").toString())
                        .header("Authorization", porteur(karim)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titre").value("Politique de télétravail"));
    }

    // ==================== CONSULTATION ====================

    @Test
    @DisplayName("le fichier d'origine se télécharge à l'identique, en pièce jointe")
    void telechargement() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        byte[] contenu = pdf(PAGE_1, PAGE_2);
        UUID id = deposer(karim, "Télétravail", contenu, "RH");

        byte[] recu = mvc.perform(get("/api/documents/" + id + "/fichier").header("Authorization", porteur(karim)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(recu).isEqualTo(contenu);
    }

    @Test
    @DisplayName("un document n'apparaît que pour les membres de ses groupes ; pour les autres il répond 404")
    void visibiliteParGroupe() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        UUID lea = compte("lea@exemple.test", Role.LECTEUR);
        UUID id = deposer(karim, "Grille salariale", pdf(PAGE_1), "RH");

        mvc.perform(get("/api/documents").header("Authorization", porteur(karim)))
                .andExpect(jsonPath("$[*].titre").value(contains("Grille salariale")));
        mvc.perform(get("/api/documents").header("Authorization", porteur(lea)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/documents/" + id).header("Authorization", porteur(lea)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + id + "/fichier").header("Authorization", porteur(lea)))
                .andExpect(status().isNotFound());
    }

    // ==================== ADMINISTRATION ====================

    @Test
    @DisplayName("administrer n'est pas lire : l'administrateur gère les droits d'un document sans accéder à son contenu")
    void administrerNEstPasLire() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        UUID id = deposer(karim, "Grille salariale", pdf(PAGE_1), "RH");

        mvc.perform(get("/api/admin/documents").header("Authorization", porteur(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titre").value("Grille salariale"))
                .andExpect(jsonPath("$[0].groupes").value(contains("RH")));
        mvc.perform(get("/api/documents/" + id).header("Authorization", porteur(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + id + "/fichier").header("Authorization", porteur(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un changement de groupes s'applique à la requête suivante et consigne les droits avant et après")
    void changementDeDroits() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        UUID ines = compte("ines@exemple.test", Role.LECTEUR, "Finance");
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR);
        UUID id = deposer(karim, "Budget formation", pdf(PAGE_1), "RH");
        String jetonKarim = porteur(karim);
        String jetonInes = porteur(ines);
        mvc.perform(get("/api/documents/" + id).header("Authorization", jetonKarim)).andExpect(status().isOk());

        mvc.perform(put("/api/admin/documents/" + id + "/groupes").header("Authorization", porteur(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("groupes", List.of(groupe("Finance"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupes").value(contains("Finance")));

        mvc.perform(get("/api/documents/" + id).header("Authorization", jetonKarim)).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + id).header("Authorization", jetonInes)).andExpect(status().isOk());
        JsonNode details = json.readTree(sql.queryForObject(
                "SELECT details::text FROM journal_audit WHERE action = 'DROITS_DOCUMENT_MODIFIES'", String.class));
        assertThat(details.get("avant").toString()).isEqualTo("[\"RH\"]");
        assertThat(details.get("apres").toString()).isEqualTo("[\"Finance\"]");
    }

    @Test
    @DisplayName("les droits d'un document refusent un groupe inconnu, et un document inconnu répond 404")
    void changementDeDroitsInvalide() throws Exception {
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR, "RH");
        UUID id = deposer(admin, "Note", pdf(PAGE_1), "RH");
        String corps = json.writeValueAsString(Map.of("groupes", List.of(UUID.randomUUID())));

        mvc.perform(put("/api/admin/documents/" + id + "/groupes").header("Authorization", porteur(admin))
                .contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/documents/" + UUID.randomUUID() + "/groupes").header("Authorization", porteur(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"groupes\":[]}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + id).header("Authorization", porteur(admin))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un document dont le dernier groupe est supprimé n'est plus visible de personne")
    void documentSansGroupe() throws Exception {
        UUID karim = compte("karim@exemple.test", Role.CONTRIBUTEUR, "RH");
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR, "RH");
        UUID id = deposer(karim, "Note RH", pdf(PAGE_1), "RH");

        mvc.perform(delete("/api/admin/groupes/" + groupe("RH")).header("Authorization", porteur(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/documents/" + id).header("Authorization", porteur(karim))).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + id).header("Authorization", porteur(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/documents").header("Authorization", porteur(admin)))
                .andExpect(jsonPath("$[0].groupes.length()").value(0));
    }

    @Test
    @DisplayName("la suppression retire le document, son fichier et ses passages")
    void suppression() throws Exception {
        UUID admin = compte("admin@exemple.test", Role.ADMINISTRATEUR, "RH");
        UUID id = deposer(admin, "Note", pdf(PAGE_1), "RH");

        mvc.perform(delete("/api/admin/documents/" + id).header("Authorization", porteur(admin)))
                .andExpect(status().isNoContent());

        assertThat(sql.queryForObject(
                "SELECT (SELECT count(*) FROM document) + (SELECT count(*) FROM passage) + (SELECT count(*) FROM document_fichier)",
                Integer.class)).isZero();
        mvc.perform(delete("/api/admin/documents/" + id).header("Authorization", porteur(admin)))
                .andExpect(status().isNotFound());
        assertThat(actionsAudit()).containsExactly("DOCUMENT_DEPOSE", "DOCUMENT_SUPPRIME");
    }

    @Test
    @DisplayName("au redémarrage, une indexation interrompue reprend")
    void repriseApresArret() {
        UUID id = UUID.randomUUID();
        byte[] contenu = pdf(PAGE_1);
        sql.update("INSERT INTO document (id, titre, nom_fichier, taille_octets, etat) VALUES (?, 'Note', 'note.pdf', ?, 'EN_COURS')",
                id, contenu.length);
        sql.update("INSERT INTO document_fichier (document_id, contenu) VALUES (?, ?)", id, contenu);

        indexeur.reprendre();

        assertThat(attendreIndexation(id)).isEqualTo("INDEXE");
        assertThat(sql.queryForObject("SELECT count(*) FROM passage WHERE document_id = ?", Integer.class, id)).isEqualTo(1);
    }
}

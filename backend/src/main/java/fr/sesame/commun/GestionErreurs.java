package fr.sesame.commun;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Traduit les erreurs en réponses JSON, sans rien révéler du fonctionnement interne */
@RestControllerAdvice
public class GestionErreurs {

    @ExceptionHandler(ErreurMetier.class)
    public ResponseEntity<ErreurApi> erreurMetier(ErreurMetier erreur) {
        var reponse = ResponseEntity.status(erreur.statut());
        if (erreur.attente() != null) {
            reponse.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, erreur.attente().toSeconds())));
        }
        return reponse.body(new ErreurApi(erreur.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApi> champsInvalides(MethodArgumentNotValidException erreur) {
        Map<String, String> champs = new LinkedHashMap<>();
        erreur.getBindingResult().getFieldErrors()
                .forEach(champ -> champs.putIfAbsent(champ.getField(), champ.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErreurApi("Requête invalide", champs));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErreurApi> fichierTropVolumineux() {
        return erreurMetier(ErreurMetier.tropVolumineux());
    }

    // Corps illisible, paramètre manquant, ou identifiant d'adresse qui n'est pas un UUID
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MultipartException.class})
    public ResponseEntity<ErreurApi> requeteIllisible() {
        return ResponseEntity.badRequest().body(new ErreurApi("Requête invalide"));
    }
}

package fr.sesame.commun;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Corps JSON de toutes les réponses d'erreur de l'API */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErreurApi(String erreur, Map<String, String> champs) {

    public ErreurApi(String erreur) {
        this(erreur, null);
    }
}

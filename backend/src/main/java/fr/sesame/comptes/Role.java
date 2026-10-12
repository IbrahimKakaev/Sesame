package fr.sesame.comptes;

/**
 * Rôles, du moins au plus étendu : chaque rôle inclut les droits du précédent
 * (voir la hiérarchie déclarée dans ConfigurationSecurite).
 */
public enum Role {
    /** Pose des questions */
    LECTEUR,
    /** Dépose des documents */
    CONTRIBUTEUR,
    /** Gère les comptes, les groupes et les droits */
    ADMINISTRATEUR
}

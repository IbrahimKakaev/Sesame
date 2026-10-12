package fr.sesame.audit;

/** Événements consignés dans le journal d'audit */
public enum ActionAudit {
    CONNEXION_REUSSIE,
    CONNEXION_ECHOUEE,
    COMPTE_CREE,
    COMPTE_MODIFIE,
    GROUPE_CREE,
    GROUPE_RENOMME,
    GROUPE_SUPPRIME,
    DOCUMENT_DEPOSE,
    DROITS_DOCUMENT_MODIFIES,
    DOCUMENT_SUPPRIME,
    RECHERCHE,
    QUESTION_POSEE
}

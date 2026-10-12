package fr.sesame.documents;

/** Avancement de l'indexation d'un document */
public enum EtatDocument {
    EN_ATTENTE,
    EN_COURS,
    /** Seul état dans lequel le document est interrogeable */
    INDEXE,
    EN_ECHEC
}

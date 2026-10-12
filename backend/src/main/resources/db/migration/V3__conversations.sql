-- Historique des questions et des réponses de chaque utilisateur.

CREATE TABLE conversation (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id uuid NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    titre          text NOT NULL,
    cree_le        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX conversation_utilisateur_idx ON conversation (utilisateur_id, cree_le DESC);

CREATE TABLE message (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    question        text NOT NULL,
    reponse         text NOT NULL,
    -- Vrai quand aucun passage autorisé ne permettait de répondre
    sans_reponse    boolean NOT NULL,
    cree_le         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX message_conversation_idx ON message (conversation_id, cree_le);

-- Une citation ne garde que la référence du passage : le titre et l'extrait sont relus,
-- sous contrôle des droits, à chaque affichage de l'historique.
CREATE TABLE citation (
    message_id uuid NOT NULL REFERENCES message (id) ON DELETE CASCADE,
    numero     integer NOT NULL,
    passage_id uuid NOT NULL REFERENCES passage (id) ON DELETE CASCADE,
    PRIMARY KEY (message_id, numero)
);

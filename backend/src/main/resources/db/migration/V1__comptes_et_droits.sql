-- Comptes, groupes et journal d'audit.

CREATE TABLE groupe (
    id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nom     text NOT NULL UNIQUE,
    -- Un groupe système ne peut être ni renommé ni supprimé
    systeme boolean NOT NULL DEFAULT false,
    cree_le timestamptz NOT NULL DEFAULT now()
);

-- Groupe commun à tous les comptes : chaque utilisateur y est inscrit d'office
INSERT INTO groupe (nom, systeme) VALUES ('Tous', true);

CREATE TABLE utilisateur (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email        text NOT NULL UNIQUE,
    nom          text NOT NULL,
    -- Empreinte bcrypt, jamais le mot de passe
    mot_de_passe text NOT NULL,
    role         text NOT NULL CHECK (role IN ('LECTEUR', 'CONTRIBUTEUR', 'ADMINISTRATEUR')),
    actif        boolean NOT NULL DEFAULT true,
    cree_le      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE utilisateur_groupe (
    utilisateur_id uuid NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    groupe_id      uuid NOT NULL REFERENCES groupe (id) ON DELETE CASCADE,
    PRIMARY KEY (utilisateur_id, groupe_id)
);

CREATE INDEX utilisateur_groupe_groupe_idx ON utilisateur_groupe (groupe_id);

-- Jetons de renouvellement : seule leur empreinte SHA-256 est conservée.
-- Les jetons issus d'une même connexion partagent une famille, révoquée d'un bloc
-- si l'un d'eux est rejoué.
CREATE TABLE jeton_renouvellement (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id uuid NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    empreinte      text NOT NULL UNIQUE,
    famille        uuid NOT NULL,
    expire_le      timestamptz NOT NULL,
    revoque        boolean NOT NULL DEFAULT false,
    cree_le        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX jeton_renouvellement_famille_idx ON jeton_renouvellement (famille);
CREATE INDEX jeton_renouvellement_utilisateur_idx ON jeton_renouvellement (utilisateur_id);

-- L'acteur n'est pas une clé étrangère : le journal survit aux comptes.
CREATE TABLE journal_audit (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date         timestamptz NOT NULL DEFAULT now(),
    acteur_id    uuid,
    acteur_email text,
    action       text NOT NULL,
    cible_type   text,
    cible_id     text,
    details      jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX journal_audit_date_idx ON journal_audit (date DESC);

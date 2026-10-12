-- Documents déposés, leurs groupes autorisés et leurs passages indexés.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    titre         text NOT NULL,
    -- Nom assaini : il ne sert qu'à l'affichage et au téléchargement, jamais de chemin sur disque
    nom_fichier   text NOT NULL,
    taille_octets bigint NOT NULL,
    nombre_pages  integer,
    etat          text NOT NULL DEFAULT 'EN_ATTENTE'
                  CHECK (etat IN ('EN_ATTENTE', 'EN_COURS', 'INDEXE', 'EN_ECHEC')),
    motif_echec   text,
    depose_par    uuid REFERENCES utilisateur (id) ON DELETE SET NULL,
    depose_le     timestamptz NOT NULL DEFAULT now(),
    indexe_le     timestamptz
);

-- Le fichier d'origine vit en base, à l'écart de la table consultée à chaque recherche
CREATE TABLE document_fichier (
    document_id uuid PRIMARY KEY REFERENCES document (id) ON DELETE CASCADE,
    contenu     bytea NOT NULL
);

-- Un utilisateur voit un document seulement s'il appartient à l'un de ces groupes.
-- Un document sans groupe n'est visible de personne.
CREATE TABLE document_groupe (
    document_id uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    groupe_id   uuid NOT NULL REFERENCES groupe (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, groupe_id)
);

CREATE INDEX document_groupe_groupe_idx ON document_groupe (groupe_id);

CREATE TABLE passage (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    page        integer NOT NULL,
    position    integer NOT NULL,
    contenu     text NOT NULL,
    -- multilingual-e5-small : 384 dimensions, vecteurs normalisés
    embedding   vector(384) NOT NULL,
    -- Recherche plein texte en français, tenue à jour par PostgreSQL
    recherche   tsvector GENERATED ALWAYS AS (to_tsvector('french', contenu)) STORED,
    UNIQUE (document_id, position)
);

CREATE INDEX passage_recherche_idx ON passage USING gin (recherche);
-- Pas d'index vectoriel approché : la recherche est exacte, parmi les seuls passages
-- autorisés. Un index HNSW filtré après coup pourrait rendre moins de résultats que demandé.

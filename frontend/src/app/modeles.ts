/**
 * Types échangés avec l'API Sésame (mêmes noms de champs que le backend)
 */

export type Role = 'LECTEUR' | 'CONTRIBUTEUR' | 'ADMINISTRATEUR';

export const LIBELLE_ROLE: Record<Role, string> = {
  LECTEUR: 'Lecteur',
  CONTRIBUTEUR: 'Contributeur',
  ADMINISTRATEUR: 'Administrateur',
};

export interface Groupe {
  id: string;
  nom: string;
  systeme: boolean;
}

export interface Profil {
  id: string;
  email: string;
  nom: string;
  role: Role;
  actif: boolean;
  groupes: Groupe[];
}

export interface Session {
  jetonAcces: string;
  expireDans: number;
  utilisateur: Profil;
}

export type EtatDocument = 'EN_ATTENTE' | 'EN_COURS' | 'INDEXE' | 'EN_ECHEC';

export const LIBELLE_ETAT: Record<EtatDocument, string> = {
  EN_ATTENTE: 'En attente',
  EN_COURS: 'Indexation en cours',
  INDEXE: 'Indexé',
  EN_ECHEC: 'En échec',
};

export interface DocumentResume {
  id: string;
  titre: string;
  nomFichier: string;
  tailleOctets: number;
  nombrePages: number | null;
  etat: EtatDocument;
  motifEchec: string | null;
  deposeLe: string;
  groupes: string[];
}

/** Source d'une réponse : le passage cité, avec son document et sa page */
export interface Citation {
  numero: number;
  passageId: string;
  documentId: string;
  titre: string;
  page: number;
  extrait: string;
}

export interface Conversation {
  id: string;
  titre: string;
  creeLe: string;
}

export interface MessageHistorique {
  id: string;
  question: string;
  reponse: string;
  sansReponse: boolean;
  creeLe: string;
  citations: Citation[];
}

export interface DetailConversation extends Conversation {
  messages: MessageHistorique[];
}

/** Événements du flux de réponse (SSE) */
export type EvenementFlux =
  | { nom: 'texte'; donnees: { texte: string } }
  | { nom: 'citation'; donnees: Citation }
  | { nom: 'fin'; donnees: { conversationId: string; messageId: string; texte: string; sansReponse: boolean } }
  | { nom: 'erreur'; donnees: { erreur: string } };

export interface LigneJournal {
  id: number;
  date: string;
  acteurEmail: string | null;
  action: string;
  cibleType: string | null;
  cibleId: string | null;
  details: Record<string, unknown>;
}

export interface PageJournal {
  lignes: LigneJournal[];
  total: number;
}

export interface CreationCompte {
  email: string;
  nom: string;
  motDePasse: string;
  role: Role;
  groupes: string[];
}

export interface ModificationCompte {
  nom: string;
  role: Role;
  actif: boolean;
  groupes: string[];
  motDePasse?: string;
}

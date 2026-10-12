/**
 * DemoBackend - Faux serveur du mode démo
 *
 * Publiée comme site statique (GitHub Pages), l'application n'a ni backend ni base.
 * Ce service joue leur rôle dans le navigateur, avec les comptes, les groupes et les
 * documents de l'entreprise fictive, et des réponses préenregistrées.
 *
 * La règle d'accès est la même que celle du vrai serveur : un compte voit un document
 * s'il partage un groupe avec lui. Une réponse préenregistrée est composée de segments
 * qui citent chacun un document ; seuls les segments dont le document est visible par
 * le compte courant sont rendus. La même question donne donc une réponse différente
 * selon le compte, et la réponse convenue quand aucune source n'est visible.
 *
 * Rien n'est enregistré : les données repartent de zéro à chaque rechargement.
 */

import { HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
  Citation,
  Conversation,
  DetailConversation,
  DocumentResume,
  EvenementFlux,
  Groupe,
  LigneJournal,
  MessageHistorique,
  Profil,
  Role,
} from '../modeles';
import donnees from './demo-donnees.json';

export const SANS_REPONSE = 'Je ne trouve pas la réponse dans les documents auxquels vous avez accès.';
export const HORS_SCENARIO =
  'La démo en ligne ne sait répondre qu’aux questions préenregistrées : choisissez-en une dans la liste proposée.';
export const MOT_DE_PASSE_DEMO: string = donnees.motDePasse;
export const QUESTIONS_DEMO: string[] = donnees.reponses.map((reponse) => reponse.question);
export const COMPTES_DEMO: { email: string; nom: string; role: Role; groupes: string[] }[] = donnees.comptes.map(
  (compte) => ({ ...compte, role: compte.role as Role }),
);

const CLE_MODE = 'sesame.demo';
const CLE_COMPTE = 'sesame.demo.compte';

interface DocumentDemo {
  id: string;
  cle: string;
  titre: string;
  groupes: string[];
  pages: string[];
}

interface ConversationDemo extends Conversation {
  compte: string;
  messages: MessageHistorique[];
}

/**
 * Vrai quand l'application doit tourner sans serveur : hébergée sur GitHub Pages,
 * ou ouverte avec ?demo dans l'adresse. Le choix est retenu pour l'onglet, la
 * navigation interne faisant disparaître ?demo.
 */
export function estModeDemo(adresse: { hostname: string; search: string } = window.location): boolean {
  const demande = adresse.hostname.endsWith('.github.io') || new URLSearchParams(adresse.search).has('demo');
  try {
    if (demande) sessionStorage.setItem(CLE_MODE, '1');
    return demande || sessionStorage.getItem(CLE_MODE) === '1';
  } catch {
    // sessionStorage indisponible (navigation privée stricte)
    return demande;
  }
}

/** Minuscules, sans accents ni ponctuation : sert à rapprocher une question des questions préenregistrées */
export function motsDe(texte: string): string[] {
  return texte
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .split(/[^a-z0-9]+/)
    .filter((mot) => mot.length > 2);
}

function refus(statut: number, erreur: string): HttpErrorResponse {
  return new HttpErrorResponse({ status: statut, error: { erreur } });
}

@Injectable({ providedIn: 'root' })
export class DemoBackend {
  // Mode démo actif ou non, décidé une fois au démarrage
  actif = estModeDemo();
  // Pause entre deux mots de la réponse, pour reproduire l'arrivée en flux
  delaiParMot = 28;
  // Court délai sur chaque appel, pour garder le rythme d'un vrai aller-retour réseau
  latence = 120;

  private groupes: Groupe[] = [];
  private comptes: Profil[] = [];
  private documents: DocumentDemo[] = [];
  private conversations: ConversationDemo[] = [];
  private journal: LigneJournal[] = [];
  private compteCourant: string | null = null;
  private compteur = 0;

  constructor() {
    this.reinitialiser();
  }

  /** Recharge l'entreprise fictive dans son état d'origine */
  reinitialiser(): void {
    this.groupes = donnees.groupes.map((nom) => ({ id: `groupe-${motsDe(nom)[0]}`, nom, systeme: nom === 'Tous' }));
    this.comptes = COMPTES_DEMO.map((compte, rang) => ({
      id: `compte-${rang + 1}`,
      email: compte.email,
      nom: compte.nom,
      role: compte.role,
      actif: true,
      groupes: this.groupesNommes(['Tous', ...compte.groupes]),
    }));
    this.documents = donnees.documents.map((document) => ({ ...document, id: `document-${document.cle}` }));
    this.conversations = [];
    this.journal = [];
    this.compteur = 0;
    this.compteCourant = this.lireCompteRetenu();
  }

  // ==================== RÈGLE D'ACCÈS ====================

  private courant(): Profil | null {
    return this.comptes.find((compte) => compte.id === this.compteCourant && compte.actif) ?? null;
  }

  /** Un compte voit un document s'il partage au moins un groupe avec lui */
  private visible(document: DocumentDemo, compte: Profil): boolean {
    return compte.groupes.some((groupe) => document.groupes.includes(groupe.nom));
  }

  private resume(document: DocumentDemo): DocumentResume {
    return {
      id: document.id,
      titre: document.titre,
      nomFichier: `${document.cle}.pdf`,
      tailleOctets: 2500,
      nombrePages: document.pages.length,
      etat: 'INDEXE',
      motifEchec: null,
      deposeLe: '2026-10-01T08:00:00Z',
      groupes: [...document.groupes].sort(),
    };
  }

  // ==================== QUESTIONS ====================

  /** Réponse préenregistrée à la question, filtrée par les droits du compte courant */
  async *repondre(
    question: string,
    conversationId: string | null,
    signal?: AbortSignal,
  ): AsyncGenerator<EvenementFlux> {
    const compte = this.courant();
    if (!compte) throw new Error('Session expirée.');

    const prevue = this.reponsePrevue(question);
    const segments = (prevue?.segments ?? []).filter((segment) => {
      const document = this.documents.find((candidat) => candidat.cle === segment.document);
      return document !== undefined && this.visible(document, compte);
    });

    let texte = '';
    const citations: Citation[] = [];
    for (const segment of segments) {
      const document = this.documents.find((candidat) => candidat.cle === segment.document)!;
      for (const mot of segment.texte.split(' ')) {
        if (signal?.aborted) return;
        const fragment = (texte ? ' ' : '') + mot;
        texte += fragment;
        yield { nom: 'texte', donnees: { texte: fragment } };
        if (this.delaiParMot > 0) await new Promise((suite) => setTimeout(suite, this.delaiParMot));
      }
      const passageId = `${document.id}:${segment.page}:${segment.extrait.length}`;
      let citation = citations.find((connue) => connue.passageId === passageId);
      if (!citation) {
        citation = {
          numero: citations.length + 1,
          passageId,
          documentId: document.id,
          titre: document.titre,
          page: segment.page,
          extrait: segment.extrait,
        };
        citations.push(citation);
      }
      texte += ` [${citation.numero}]`;
      yield { nom: 'citation', donnees: citation };
    }

    const sansReponse = citations.length === 0;
    if (sansReponse) texte = prevue ? SANS_REPONSE : HORS_SCENARIO;

    const conversation = this.conversationPour(compte, conversationId, question);
    const message: MessageHistorique = {
      id: this.identifiant('message'),
      question,
      reponse: texte,
      sansReponse,
      creeLe: new Date().toISOString(),
      citations,
    };
    conversation.messages.push(message);
    this.consigner(compte, 'QUESTION_POSEE', {
      question,
      documentsCites: [...new Set(citations.map((citation) => citation.titre))],
      sansReponse,
    });
    yield { nom: 'fin', donnees: { conversationId: conversation.id, messageId: message.id, texte, sansReponse } };
  }

  /** Question préenregistrée la plus proche, si elle partage assez de mots-clés avec la question posée */
  private reponsePrevue(question: string) {
    const mots = new Set(motsDe(question));
    let meilleure: (typeof donnees.reponses)[number] | undefined;
    let meilleurScore = 0;
    for (const reponse of donnees.reponses) {
      const communs = reponse.motsCles.filter((mot) => mots.has(mot)).length;
      if (communs > meilleurScore) {
        meilleurScore = communs;
        meilleure = reponse;
      }
    }
    return meilleurScore >= 2 || (meilleure && motsDe(meilleure.question).every((mot) => mots.has(mot)))
      ? meilleure
      : undefined;
  }

  private conversationPour(compte: Profil, id: string | null, question: string): ConversationDemo {
    const existante = this.conversations.find((c) => c.id === id && c.compte === compte.id);
    if (existante) return existante;
    const creee: ConversationDemo = {
      id: this.identifiant('conversation'),
      compte: compte.id,
      titre: question.length <= 80 ? question : question.slice(0, 79) + '…',
      creeLe: new Date().toISOString(),
      messages: [],
    };
    this.conversations.unshift(creee);
    return creee;
  }

  // ==================== ROUTES HTTP ====================

  /** Répond à une requête de l'application comme le ferait le vrai serveur */
  traiter(requete: HttpRequest<unknown>): HttpResponse<unknown> {
    const chemin = requete.url.split('?')[0].replace(/^\/api\//, '');
    const route = `${requete.method} ${chemin}`;
    const corps = (requete.body ?? {}) as Record<string, unknown>;
    const reponse = (donneesReponse: unknown, statut = 200) => new HttpResponse({ status: statut, body: donneesReponse });

    // --- Authentification ---
    if (route === 'POST auth/connexion') {
      const email = String(corps['email'] ?? '').trim().toLowerCase();
      const compte = this.comptes.find((candidat) => candidat.email === email && candidat.actif);
      if (!compte || corps['motDePasse'] !== MOT_DE_PASSE_DEMO) {
        this.consigner(null, 'CONNEXION_ECHOUEE', {}, email);
        throw refus(401, 'Identifiants invalides');
      }
      this.retenirCompte(compte.id);
      this.consigner(compte, 'CONNEXION_REUSSIE', {});
      return reponse(this.session(compte));
    }
    if (route === 'POST auth/renouvellement') {
      const compte = this.courant();
      if (!compte) throw refus(401, 'Non authentifié');
      return reponse(this.session(compte));
    }
    if (route === 'POST auth/deconnexion') {
      this.retenirCompte(null);
      return reponse(null, 204);
    }

    const compte = this.courant();
    if (!compte) throw refus(401, 'Non authentifié');
    if (route === 'GET moi') return reponse(compte);

    // --- Documents ---
    if (route === 'GET documents') {
      return reponse(this.documents.filter((document) => this.visible(document, compte)).map((d) => this.resume(d)));
    }
    if (route === 'POST documents') {
      throw refus(403, 'Le dépôt de fichiers est désactivé dans la démo en ligne.');
    }
    if (/^GET documents\/[^/]+\/fichier$/.test(route)) {
      throw refus(404, 'Introuvable');
    }

    // --- Conversations ---
    if (route === 'GET conversations') {
      return reponse(
        this.conversations.filter((c) => c.compte === compte.id).map(({ id, titre, creeLe }) => ({ id, titre, creeLe })),
      );
    }
    const conversation = /^(GET|DELETE) conversations\/([^/]+)$/.exec(route);
    if (conversation) {
      const trouvee = this.conversations.find((c) => c.id === conversation[2] && c.compte === compte.id);
      if (!trouvee) throw refus(404, 'Introuvable');
      if (conversation[1] === 'DELETE') {
        this.conversations = this.conversations.filter((c) => c !== trouvee);
        return reponse(null, 204);
      }
      return reponse(this.detail(trouvee, compte));
    }

    // --- Administration ---
    if (chemin.startsWith('admin/')) {
      if (compte.role !== 'ADMINISTRATEUR') throw refus(403, 'Accès refusé');
      return reponse(...this.administrer(route, corps, compte));
    }
    throw refus(404, 'Introuvable');
  }

  private administrer(route: string, corps: Record<string, unknown>, acteur: Profil): [unknown, number?] {
    if (route === 'GET admin/utilisateurs') return [[...this.comptes].sort((a, b) => a.email.localeCompare(b.email))];
    if (route === 'GET admin/groupes') return [[...this.groupes].sort((a, b) => a.nom.localeCompare(b.nom))];
    if (route === 'GET admin/documents') return [this.documents.map((document) => this.resume(document))];
    if (route === 'GET admin/journal') return [{ lignes: this.journal, total: this.journal.length }];

    if (route === 'POST admin/utilisateurs') {
      const email = String(corps['email'] ?? '').trim().toLowerCase();
      if (this.comptes.some((compte) => compte.email === email)) throw refus(409, 'Un compte utilise déjà cet e-mail');
      const cree: Profil = {
        id: this.identifiant('compte'),
        email,
        nom: String(corps['nom'] ?? ''),
        role: corps['role'] as Role,
        actif: true,
        groupes: this.groupesParId(['groupe-tous', ...((corps['groupes'] as string[]) ?? [])]),
      };
      this.comptes.push(cree);
      this.consigner(acteur, 'COMPTE_CREE', this.etatDe(cree), undefined, cree.id);
      return [cree, 201];
    }
    const modification = /^PUT admin\/utilisateurs\/([^/]+)$/.exec(route);
    if (modification) {
      const cible = this.comptes.find((compte) => compte.id === modification[1]);
      if (!cible) throw refus(404, 'Introuvable');
      if (cible.id === acteur.id && (corps['role'] !== 'ADMINISTRATEUR' || corps['actif'] === false)) {
        throw refus(409, 'Impossible de retirer ses propres droits d’administration');
      }
      const avant = this.etatDe(cible);
      cible.nom = String(corps['nom'] ?? cible.nom);
      cible.role = corps['role'] as Role;
      cible.actif = corps['actif'] !== false;
      cible.groupes = this.groupesParId(['groupe-tous', ...((corps['groupes'] as string[]) ?? [])]);
      this.consigner(acteur, 'COMPTE_MODIFIE', { avant, apres: this.etatDe(cible) }, undefined, cible.id);
      return [cible];
    }
    if (route === 'POST admin/groupes') {
      const nom = String(corps['nom'] ?? '').trim();
      if (!nom) throw refus(400, 'Requête invalide');
      if (this.groupes.some((groupe) => groupe.nom.toLowerCase() === nom.toLowerCase())) {
        throw refus(409, 'Un groupe porte déjà ce nom');
      }
      const groupe: Groupe = { id: this.identifiant('groupe'), nom, systeme: false };
      this.groupes.push(groupe);
      this.consigner(acteur, 'GROUPE_CREE', { nom }, undefined, groupe.id);
      return [groupe, 201];
    }
    const suppressionGroupe = /^DELETE admin\/groupes\/([^/]+)$/.exec(route);
    if (suppressionGroupe) {
      const groupe = this.groupes.find((candidat) => candidat.id === suppressionGroupe[1]);
      if (!groupe) throw refus(404, 'Introuvable');
      if (groupe.systeme) throw refus(409, 'Ce groupe ne peut être ni renommé ni supprimé');
      this.groupes = this.groupes.filter((candidat) => candidat !== groupe);
      this.comptes.forEach((compte) => (compte.groupes = compte.groupes.filter((g) => g.id !== groupe.id)));
      this.documents.forEach((document) => (document.groupes = document.groupes.filter((nom) => nom !== groupe.nom)));
      this.consigner(acteur, 'GROUPE_SUPPRIME', { nom: groupe.nom }, undefined, groupe.id);
      return [null, 204];
    }
    const droits = /^PUT admin\/documents\/([^/]+)\/groupes$/.exec(route);
    if (droits) {
      const document = this.documents.find((candidat) => candidat.id === droits[1]);
      if (!document) throw refus(404, 'Introuvable');
      const avant = [...document.groupes].sort();
      document.groupes = this.groupesParId((corps['groupes'] as string[]) ?? []).map((groupe) => groupe.nom);
      this.consigner(
        acteur,
        'DROITS_DOCUMENT_MODIFIES',
        { titre: document.titre, avant, apres: [...document.groupes].sort() },
        undefined,
        document.id,
      );
      return [this.resume(document)];
    }
    const suppressionDocument = /^DELETE admin\/documents\/([^/]+)$/.exec(route);
    if (suppressionDocument) {
      const document = this.documents.find((candidat) => candidat.id === suppressionDocument[1]);
      if (!document) throw refus(404, 'Introuvable');
      this.documents = this.documents.filter((candidat) => candidat !== document);
      this.consigner(acteur, 'DOCUMENT_SUPPRIME', { titre: document.titre }, undefined, document.id);
      return [null, 204];
    }
    throw refus(404, 'Introuvable');
  }

  // ==================== OUTILS ====================

  private session(compte: Profil) {
    return { jetonAcces: 'jeton-de-demonstration', expireDans: 600, utilisateur: compte };
  }

  /** Historique relu avec les droits du moment : une source devenue interdite n'est plus rendue */
  private detail(conversation: ConversationDemo, compte: Profil): DetailConversation {
    const { id, titre, creeLe, messages } = conversation;
    return {
      id,
      titre,
      creeLe,
      messages: messages.map((message) => ({
        ...message,
        citations: message.citations.filter((citation) => {
          const document = this.documents.find((candidat) => candidat.id === citation.documentId);
          return document !== undefined && this.visible(document, compte);
        }),
      })),
    };
  }

  private groupesNommes(noms: string[]): Groupe[] {
    return this.groupes.filter((groupe) => noms.includes(groupe.nom));
  }

  private groupesParId(identifiants: string[]): Groupe[] {
    return this.groupes.filter((groupe) => identifiants.includes(groupe.id));
  }

  private etatDe(compte: Profil) {
    return { email: compte.email, role: compte.role, actif: compte.actif, groupes: compte.groupes.map((g) => g.nom).sort() };
  }

  private consigner(
    acteur: Profil | null,
    action: string,
    details: Record<string, unknown>,
    email?: string,
    cibleId?: string,
  ): void {
    this.journal.unshift({
      id: this.journal.length + 1,
      date: new Date().toISOString(),
      acteurEmail: acteur?.email ?? email ?? null,
      action,
      cibleType: null,
      cibleId: cibleId ?? null,
      details,
    });
  }

  private identifiant(prefixe: string): string {
    this.compteur += 1;
    return `${prefixe}-demo-${this.compteur}`;
  }

  private lireCompteRetenu(): string | null {
    try {
      return sessionStorage.getItem(CLE_COMPTE);
    } catch {
      return null;
    }
  }

  private retenirCompte(id: string | null): void {
    this.compteCourant = id;
    try {
      if (id) sessionStorage.setItem(CLE_COMPTE, id);
      else sessionStorage.removeItem(CLE_COMPTE);
    } catch {
      // Sans sessionStorage, la session de démo ne survit pas au rechargement
    }
  }
}

/**
 * PageAdministration - Comptes, groupes, droits des documents et journal d'audit
 *
 * L'administrateur gère les droits de tous les documents sans pouvoir en lire le
 * contenu : ses propres questions passent par ses groupes, comme pour tout le monde.
 */

import { DatePipe, JsonPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { DocumentResume, Groupe, LIBELLE_ROLE, LigneJournal, Profil, Role } from '../modeles';
import { ApiService } from '../services/api';
import { SessionService } from '../services/session';

type Onglet = 'comptes' | 'groupes' | 'documents' | 'journal';

@Component({
  selector: 'app-page-administration',
  imports: [FormsModule, DatePipe, JsonPipe],
  template: `
    <section class="page">
      <h1>Administration</h1>
      <div class="onglets" role="tablist">
        @for (onglet of onglets; track onglet.cle) {
          <button type="button" role="tab" [attr.aria-selected]="actif() === onglet.cle" [class.actif]="actif() === onglet.cle" (click)="afficher(onglet.cle)">
            {{ onglet.titre }}
          </button>
        }
      </div>
      @if (message(); as texte) {
        <p [class.erreur]="enErreur()" [class.note]="!enErreur()" role="status">{{ texte }}</p>
      }

      @switch (actif()) {
        @case ('comptes') {
          <table>
            <thead><tr><th>Compte</th><th>Rôle</th><th>Groupes</th><th>Actif</th><th></th></tr></thead>
            <tbody>
              @for (compte of comptes(); track compte.id) {
                <tr>
                  <td><strong>{{ compte.nom }}</strong><br /><span class="discret">{{ compte.email }}</span></td>
                  <td>
                    <select [ngModel]="compte.role" (ngModelChange)="changerRole(compte, $event)" [attr.aria-label]="'Rôle de ' + compte.nom">
                      @for (role of roles; track role) {
                        <option [value]="role">{{ libelleRole[role] }}</option>
                      }
                    </select>
                  </td>
                  <td>
                    @for (groupe of groupes(); track groupe.id) {
                      <label class="case">
                        <input type="checkbox" [checked]="appartient(compte, groupe)" [disabled]="groupe.systeme" (change)="basculerGroupe(compte, groupe)" />
                        {{ groupe.nom }}
                      </label>
                    }
                  </td>
                  <td>
                    <input type="checkbox" [checked]="compte.actif" (change)="basculerActif(compte)" [attr.aria-label]="'Compte actif : ' + compte.nom" />
                  </td>
                  <td><button type="button" class="bouton" (click)="enregistrerCompte(compte)">Enregistrer</button></td>
                </tr>
              }
            </tbody>
          </table>
          <form class="carte" (ngSubmit)="creerCompte()">
            <h2>Nouveau compte</h2>
            <div class="ligne">
              <label>Nom <input name="nom" [(ngModel)]="nouveau.nom" required /></label>
              <label>E-mail <input name="email" type="email" [(ngModel)]="nouveau.email" required /></label>
              <label>Mot de passe (10 caractères au moins) <input name="motDePasse" type="password" [(ngModel)]="nouveau.motDePasse" minlength="10" autocomplete="new-password" required /></label>
              <label>Rôle
                <select name="role" [(ngModel)]="nouveau.role">
                  @for (role of roles; track role) {
                    <option [value]="role">{{ libelleRole[role] }}</option>
                  }
                </select>
              </label>
            </div>
            <button type="submit" class="bouton principal" [disabled]="!nouveau.nom || !nouveau.email || nouveau.motDePasse.length < 10">Créer le compte</button>
          </form>
        }
        @case ('groupes') {
          <ul class="liste">
            @for (groupe of groupes(); track groupe.id) {
              <li>
                <span class="etiquette">{{ groupe.nom }}</span>
                @if (groupe.systeme) {
                  <span class="discret">groupe commun à tous les comptes</span>
                } @else {
                  <button type="button" class="bouton discret" (click)="supprimerGroupe(groupe)">Supprimer</button>
                }
              </li>
            }
          </ul>
          <form class="carte ligne" (ngSubmit)="creerGroupe()">
            <label>Nouveau groupe <input name="nomGroupe" [(ngModel)]="nomGroupe" maxlength="60" required /></label>
            <button type="submit" class="bouton principal" [disabled]="!nomGroupe.trim()">Créer</button>
          </form>
        }
        @case ('documents') {
          <p class="discret">Un document n'est visible que des membres des groupes cochés. Le changement s'applique à la requête suivante.</p>
          <table>
            <thead><tr><th>Document</th><th>Groupes autorisés</th><th></th></tr></thead>
            <tbody>
              @for (document of documents(); track document.id) {
                <tr>
                  <td>{{ document.titre }}</td>
                  <td>
                    @for (groupe of groupes(); track groupe.id) {
                      <label class="case">
                        <input type="checkbox" [checked]="document.groupes.includes(groupe.nom)" (change)="basculerDroit(document, groupe)" />
                        {{ groupe.nom }}
                      </label>
                    }
                  </td>
                  <td><button type="button" class="bouton discret" (click)="supprimerDocument(document)">Supprimer</button></td>
                </tr>
              }
            </tbody>
          </table>
        }
        @case ('journal') {
          <table class="journal">
            <thead><tr><th>Date</th><th>Auteur</th><th>Action</th><th>Détails</th></tr></thead>
            <tbody>
              @for (ligne of journal(); track ligne.id) {
                <tr>
                  <td>{{ ligne.date | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td>{{ ligne.acteurEmail ?? '–' }}</td>
                  <td><code>{{ ligne.action }}</code></td>
                  <td><code>{{ ligne.details | json }}</code></td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="discret">Journal vide.</td></tr>
              }
            </tbody>
          </table>
        }
      }
    </section>
  `,
})
export class PageAdministration {
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);

  protected readonly onglets: { cle: Onglet; titre: string }[] = [
    { cle: 'comptes', titre: 'Comptes' },
    { cle: 'groupes', titre: 'Groupes' },
    { cle: 'documents', titre: 'Droits des documents' },
    { cle: 'journal', titre: 'Journal d’audit' },
  ];
  protected readonly roles: Role[] = ['LECTEUR', 'CONTRIBUTEUR', 'ADMINISTRATEUR'];
  protected readonly libelleRole = LIBELLE_ROLE;

  protected readonly actif = signal<Onglet>('comptes');
  protected readonly comptes = signal<Profil[]>([]);
  protected readonly groupes = signal<Groupe[]>([]);
  protected readonly documents = signal<DocumentResume[]>([]);
  protected readonly journal = signal<LigneJournal[]>([]);
  protected readonly message = signal<string | null>(null);
  protected readonly enErreur = signal(false);
  protected nouveau = { nom: '', email: '', motDePasse: '', role: 'LECTEUR' as Role };
  protected nomGroupe = '';

  constructor() {
    this.charger();
  }

  protected afficher(onglet: Onglet): void {
    this.actif.set(onglet);
    this.message.set(null);
    this.charger();
  }

  // ==================== COMPTES ====================

  protected appartient(compte: Profil, groupe: Groupe): boolean {
    return compte.groupes.some((candidat) => candidat.id === groupe.id);
  }

  protected changerRole(compte: Profil, role: Role): void {
    this.remplacerCompte({ ...compte, role });
  }

  protected basculerActif(compte: Profil): void {
    this.remplacerCompte({ ...compte, actif: !compte.actif });
  }

  protected basculerGroupe(compte: Profil, groupe: Groupe): void {
    const groupes = this.appartient(compte, groupe)
      ? compte.groupes.filter((candidat) => candidat.id !== groupe.id)
      : [...compte.groupes, groupe];
    this.remplacerCompte({ ...compte, groupes });
  }

  protected enregistrerCompte(compte: Profil): void {
    const demande = {
      nom: compte.nom,
      role: compte.role,
      actif: compte.actif,
      groupes: compte.groupes.filter((groupe) => !groupe.systeme).map((groupe) => groupe.id),
    };
    this.api.modifierUtilisateur(compte.id, demande).subscribe({
      next: (enregistre) => {
        this.remplacerCompte(enregistre);
        this.confirmer(`Droits de ${enregistre.nom} enregistrés : ils s'appliquent dès sa prochaine requête.`);
        // L'administrateur a pu modifier ses propres groupes
        if (enregistre.id === this.session.profil()?.id) this.session.rafraichirProfil().subscribe();
      },
      error: (erreur: HttpErrorResponse) => this.signaler(erreur),
    });
  }

  protected creerCompte(): void {
    this.api.creerUtilisateur({ ...this.nouveau, groupes: [] }).subscribe({
      next: (cree) => {
        this.nouveau = { nom: '', email: '', motDePasse: '', role: 'LECTEUR' };
        this.confirmer(`Compte ${cree.email} créé.`);
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => this.signaler(erreur),
    });
  }

  private remplacerCompte(compte: Profil): void {
    this.comptes.update((comptes) => comptes.map((candidat) => (candidat.id === compte.id ? compte : candidat)));
  }

  // ==================== GROUPES ====================

  protected creerGroupe(): void {
    this.api.creerGroupe(this.nomGroupe.trim()).subscribe({
      next: (groupe) => {
        this.nomGroupe = '';
        this.confirmer(`Groupe ${groupe.nom} créé.`);
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => this.signaler(erreur),
    });
  }

  protected supprimerGroupe(groupe: Groupe): void {
    this.api.supprimerGroupe(groupe.id).subscribe({
      next: () => {
        this.confirmer(`Groupe ${groupe.nom} supprimé : ses membres et ses documents en sont détachés.`);
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => this.signaler(erreur),
    });
  }

  // ==================== DOCUMENTS ====================

  protected basculerDroit(document: DocumentResume, groupe: Groupe): void {
    const noms = document.groupes.includes(groupe.nom)
      ? document.groupes.filter((nom) => nom !== groupe.nom)
      : [...document.groupes, groupe.nom];
    const identifiants = this.groupes().filter((candidat) => noms.includes(candidat.nom)).map((candidat) => candidat.id);
    this.api.changerGroupesDocument(document.id, identifiants).subscribe({
      next: (enregistre) => {
        this.documents.update((documents) => documents.map((d) => (d.id === enregistre.id ? enregistre : d)));
        this.confirmer(`Droits de « ${enregistre.titre} » enregistrés.`);
      },
      error: (erreur: HttpErrorResponse) => this.signaler(erreur),
    });
  }

  protected supprimerDocument(document: DocumentResume): void {
    this.api.supprimerDocument(document.id).subscribe({
      next: () => {
        this.confirmer(`« ${document.titre} » supprimé.`);
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => this.signaler(erreur),
    });
  }

  // ==================== OUTILS ====================

  private charger(): void {
    if (this.actif() === 'journal') {
      this.api.journal().subscribe((page) => this.journal.set(page.lignes));
      return;
    }
    forkJoin({
      comptes: this.api.utilisateurs(),
      groupes: this.api.groupes(),
      documents: this.api.tousLesDocuments(),
    }).subscribe(({ comptes, groupes, documents }) => {
      this.comptes.set(comptes);
      this.groupes.set(groupes);
      this.documents.set(documents);
    });
  }

  private confirmer(texte: string): void {
    this.enErreur.set(false);
    this.message.set(texte);
  }

  private signaler(erreur: HttpErrorResponse): void {
    this.enErreur.set(true);
    this.message.set(erreur.error?.erreur ?? 'L’opération a échoué.');
    this.charger();
  }
}

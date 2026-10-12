/**
 * PageDocuments - Documents visibles par le compte, état d'indexation et dépôt
 */

import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DemoBackend } from '../demo/demo-backend';
import { DocumentResume, Groupe, LIBELLE_ETAT } from '../modeles';
import { ApiService } from '../services/api';
import { SessionService } from '../services/session';

// Tant qu'un document est en cours d'indexation, la liste est relue à cet intervalle
const INTERVALLE_SUIVI_MS = 1500;
const TAILLE_MAX_OCTETS = 10 * 1024 * 1024;

@Component({
  selector: 'app-page-documents',
  imports: [FormsModule, DatePipe],
  template: `
    <section class="page">
      <h1>Documents</h1>
      <p class="discret">Seuls les documents partagés avec l'un de vos groupes apparaissent ici.</p>

      @if (session.peutDeposer()) {
        <form class="carte depot" (ngSubmit)="deposer()">
          <h2>Déposer un PDF</h2>
          <label>
            Fichier (PDF, 10 Mo au plus)
            <input type="file" accept="application/pdf,.pdf" (change)="choisir($event)" />
          </label>
          <label>
            Titre (facultatif)
            <input name="titre" [(ngModel)]="titre" maxlength="150" />
          </label>
          <fieldset>
            <legend>Groupes autorisés à le consulter</legend>
            @for (groupe of groupesProposes(); track groupe.id) {
              <label class="case">
                <input type="checkbox" [checked]="choisis().includes(groupe.id)" (change)="basculer(groupe.id)" />
                {{ groupe.nom }}
              </label>
            }
          </fieldset>
          @if (message(); as texte) {
            <p [class.erreur]="enErreur()" [class.note]="!enErreur()" role="status">{{ texte }}</p>
          }
          <button type="submit" class="bouton principal" [disabled]="!fichier() || choisis().length === 0 || envoi()">
            Déposer
          </button>
        </form>
      }

      <table>
        <thead>
          <tr><th>Titre</th><th>Groupes</th><th>Pages</th><th>État</th><th>Déposé le</th><th></th></tr>
        </thead>
        <tbody>
          @for (document of documents(); track document.id) {
            <tr>
              <td>{{ document.titre }}</td>
              <td>
                @for (groupe of document.groupes; track groupe) {
                  <span class="etiquette">{{ groupe }}</span>
                }
              </td>
              <td>{{ document.nombrePages ?? '–' }}</td>
              <td>
                <span class="etat" [attr.data-etat]="document.etat">{{ libelleEtat[document.etat] }}</span>
                @if (document.motifEchec) {
                  <small class="erreur">{{ document.motifEchec }}</small>
                }
              </td>
              <td>{{ document.deposeLe | date: 'dd/MM/yyyy' }}</td>
              <td>
                @if (!demo.actif) {
                  <button type="button" class="bouton discret" (click)="telecharger(document)">Télécharger</button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="6" class="discret">Aucun document accessible.</td></tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class PageDocuments {
  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);
  protected readonly demo = inject(DemoBackend);
  protected readonly libelleEtat = LIBELLE_ETAT;

  protected readonly documents = signal<DocumentResume[]>([]);
  protected readonly groupesProposes = signal<Groupe[]>([]);
  protected readonly choisis = signal<string[]>([]);
  protected readonly fichier = signal<File | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly enErreur = signal(false);
  protected readonly envoi = signal(false);
  protected titre = '';
  private suivi: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.arreterSuivi());
    effect(() => {
      const profil = this.session.profil();
      untracked(() => {
        if (!profil) return;
        this.choisis.set([]);
        // Un contributeur dépose dans ses groupes ; un administrateur, dans n'importe lequel
        if (this.session.estAdministrateur()) {
          this.api.groupes().subscribe((groupes) => this.groupesProposes.set(groupes));
        } else {
          this.groupesProposes.set(profil.groupes);
        }
        this.charger();
      });
    });
  }

  protected choisir(evenement: Event): void {
    const fichier = (evenement.target as HTMLInputElement).files?.[0] ?? null;
    this.message.set(null);
    this.enErreur.set(false);
    if (fichier && fichier.size > TAILLE_MAX_OCTETS) {
      this.fichier.set(null);
      this.afficherErreur('Ce fichier dépasse 10 Mo.');
      return;
    }
    this.fichier.set(fichier);
  }

  protected basculer(groupe: string): void {
    this.choisis.update((choisis) =>
      choisis.includes(groupe) ? choisis.filter((id) => id !== groupe) : [...choisis, groupe],
    );
  }

  protected deposer(): void {
    const fichier = this.fichier();
    if (!fichier) return;
    this.envoi.set(true);
    this.api.deposer(fichier, this.titre, this.choisis()).subscribe({
      next: (document) => {
        this.envoi.set(false);
        this.enErreur.set(false);
        this.message.set(`« ${document.titre} » est déposé. Son indexation est en cours.`);
        this.titre = '';
        this.charger();
      },
      error: (erreur: HttpErrorResponse) => {
        this.envoi.set(false);
        this.afficherErreur(erreur.error?.erreur ?? 'Le dépôt a échoué.');
      },
    });
  }

  protected telecharger(document: DocumentResume): void {
    this.api.fichier(document.id).subscribe((contenu) => {
      const adresse = URL.createObjectURL(contenu);
      const lien = window.document.createElement('a');
      lien.href = adresse;
      lien.download = document.nomFichier;
      lien.click();
      URL.revokeObjectURL(adresse);
    });
  }

  private charger(): void {
    this.arreterSuivi();
    this.api.documents().subscribe((documents) => {
      this.documents.set(documents);
      if (documents.some((document) => document.etat === 'EN_ATTENTE' || document.etat === 'EN_COURS')) {
        this.suivi = setTimeout(() => this.charger(), INTERVALLE_SUIVI_MS);
      }
    });
  }

  private arreterSuivi(): void {
    if (this.suivi) clearTimeout(this.suivi);
    this.suivi = null;
  }

  private afficherErreur(texte: string): void {
    this.enErreur.set(true);
    this.message.set(texte);
  }
}

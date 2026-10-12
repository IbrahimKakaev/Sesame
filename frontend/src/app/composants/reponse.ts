/**
 * Reponse - Affiche une réponse et ses sources
 *
 * Le texte contient des renvois « [1] », « [2] »... posés par le serveur à l'endroit où
 * chaque source s'applique. Un renvoi devient un bouton qui ouvre l'extrait cité. Un
 * renvoi dont la source n'est plus accessible (droit retiré depuis) reste un simple texte.
 */

import { Component, computed, input, signal } from '@angular/core';
import { Citation } from '../modeles';

interface Morceau {
  texte: string;
  citation?: Citation;
}

/** Découpe le texte en morceaux de texte et renvois vers une source connue */
export function decouperReponse(texte: string, citations: Citation[]): Morceau[] {
  const morceaux: Morceau[] = [];
  for (const partie of texte.split(/(\[\d+\])/)) {
    if (!partie) continue;
    const renvoi = /^\[(\d+)\]$/.exec(partie);
    const citation = renvoi ? citations.find((candidate) => candidate.numero === Number(renvoi[1])) : undefined;
    morceaux.push(citation ? { texte: partie, citation } : { texte: partie });
  }
  return morceaux;
}

@Component({
  selector: 'app-reponse',
  template: `
    <p class="texte-reponse" [class.sans-reponse]="sansReponse()">
      @for (morceau of morceaux(); track $index) {
        @if (morceau.citation; as citation) {
          <button
            type="button"
            class="renvoi"
            [class.ouvert]="ouverte() === citation.numero"
            [attr.aria-label]="'Source ' + citation.numero + ' : ' + citation.titre + ', page ' + citation.page"
            (click)="basculer(citation.numero)"
          >
            {{ citation.numero }}
          </button>
        } @else {
          <span>{{ morceau.texte }}</span>
        }
      }
      @if (enCours()) {
        <span class="curseur" aria-hidden="true"></span>
      }
    </p>
    @if (citations().length > 0) {
      <ul class="sources" aria-label="Sources de la réponse">
        @for (citation of sources(); track citation.numero) {
          <li class="source" [class.ouverte]="ouverte() === citation.numero">
            <button type="button" class="titre-source" [attr.aria-expanded]="ouverte() === citation.numero" (click)="basculer(citation.numero)">
              <span class="numero">{{ citation.numero }}</span>
              <span>{{ citation.titre }}</span>
              <span class="discret">page {{ citation.page }}</span>
            </button>
            @if (ouverte() === citation.numero) {
              <blockquote>{{ citation.extrait }}</blockquote>
            }
          </li>
        }
      </ul>
    }
  `,
})
export class Reponse {
  readonly texte = input.required<string>();
  readonly citations = input<Citation[]>([]);
  readonly sansReponse = input(false);
  readonly enCours = input(false);

  protected readonly ouverte = signal<number | null>(null);
  protected readonly morceaux = computed(() => decouperReponse(this.texte(), this.citations()));
  /** Une ligne par source, même si elle est citée plusieurs fois */
  protected readonly sources = computed(() =>
    this.citations()
      .filter((citation, rang, toutes) => toutes.findIndex((c) => c.numero === citation.numero) === rang)
      .sort((a, b) => a.numero - b.numero),
  );

  protected basculer(numero: number): void {
    this.ouverte.update((courante) => (courante === numero ? null : numero));
  }
}

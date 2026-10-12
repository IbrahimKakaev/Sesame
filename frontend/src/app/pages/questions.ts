/**
 * PageQuestions - Poser une question, lire la réponse en flux, retrouver l'historique
 */

import { Component, effect, inject, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { Reponse } from '../composants/reponse';
import { DemoBackend, QUESTIONS_DEMO } from '../demo/demo-backend';
import { Citation, Conversation } from '../modeles';
import { ApiService } from '../services/api';
import { QuestionsService } from '../services/questions';
import { SessionService } from '../services/session';

interface Echange {
  question: string;
  texte: string;
  citations: Citation[];
  sansReponse: boolean;
  enCours: boolean;
  erreur: string | null;
}

const EXEMPLES = [
  'Combien de jours de télétravail par semaine ?',
  'Quelle est la longueur minimale d’un mot de passe ?',
  'Jusqu’à quel montant un repas en déplacement est-il remboursé ?',
];

@Component({
  selector: 'app-page-questions',
  imports: [Reponse],
  template: `
    <div class="questions">
      <aside class="historique" aria-label="Historique des conversations">
        <button type="button" class="bouton" (click)="nouvelle()">Nouvelle conversation</button>
        <ul>
          @for (conversation of conversations(); track conversation.id) {
            <li [class.courante]="conversation.id === conversationId()">
              <button type="button" class="lien" (click)="ouvrir(conversation.id)">{{ conversation.titre }}</button>
              <button type="button" class="supprimer" [attr.aria-label]="'Supprimer : ' + conversation.titre" (click)="supprimer(conversation.id)">×</button>
            </li>
          } @empty {
            <li class="discret">Aucune conversation pour l'instant.</li>
          }
        </ul>
      </aside>

      <section class="fil" aria-live="polite">
        @if (echanges().length === 0) {
          <div class="accueil">
            <h1>Que cherchez-vous ?</h1>
            <p class="discret">
              La réponse ne s'appuie que sur les documents que vous avez le droit de consulter, et cite chaque
              source.
            </p>
          </div>
        }
        @for (echange of echanges(); track $index) {
          <article class="echange">
            <h2 class="question">{{ echange.question }}</h2>
            @if (echange.erreur) {
              <p class="erreur" role="alert">{{ echange.erreur }}</p>
            } @else {
              <app-reponse [texte]="echange.texte" [citations]="echange.citations" [sansReponse]="echange.sansReponse" [enCours]="echange.enCours" />
            }
          </article>
        }

        <div class="suggestions">
          @for (suggestion of suggestions; track suggestion) {
            <button type="button" class="suggestion" [disabled]="enCours()" (click)="poser(suggestion)">{{ suggestion }}</button>
          }
        </div>
        <form class="saisie" (submit)="$event.preventDefault(); poser(question())">
          <label class="invisible" for="question">Votre question</label>
          <input id="question" name="question" [value]="question()" (input)="question.set($any($event.target).value)" maxlength="500" placeholder="Posez votre question…" autocomplete="off" />
          <button type="submit" class="bouton principal" [disabled]="enCours() || !question().trim()">Demander</button>
        </form>
      </section>
    </div>
  `,
})
export class PageQuestions {
  private readonly api = inject(ApiService);
  private readonly service = inject(QuestionsService);
  private readonly session = inject(SessionService);
  private readonly demo = inject(DemoBackend);

  protected readonly suggestions = this.demo.actif ? QUESTIONS_DEMO : EXEMPLES;
  protected readonly conversations = signal<Conversation[]>([]);
  protected readonly conversationId = signal<string | null>(null);
  protected readonly echanges = signal<Echange[]>([]);
  protected readonly enCours = signal(false);
  protected readonly question = signal('');
  private abonnement: Subscription | null = null;

  constructor() {
    // Changement de compte (bandeau de démo) : l'historique affiché est celui du nouveau compte
    effect(() => {
      const compte = this.session.profil()?.id;
      untracked(() => {
        this.nouvelle();
        if (compte) this.chargerConversations();
      });
    });
  }

  protected nouvelle(): void {
    this.abonnement?.unsubscribe();
    this.enCours.set(false);
    this.conversationId.set(null);
    this.echanges.set([]);
  }

  protected ouvrir(id: string): void {
    this.nouvelle();
    this.api.conversation(id).subscribe((detail) => {
      this.conversationId.set(detail.id);
      this.echanges.set(
        detail.messages.map((message) => ({
          question: message.question,
          texte: message.reponse,
          citations: message.citations,
          sansReponse: message.sansReponse,
          enCours: false,
          erreur: null,
        })),
      );
    });
  }

  protected supprimer(id: string): void {
    this.api.supprimerConversation(id).subscribe(() => {
      if (this.conversationId() === id) this.nouvelle();
      this.chargerConversations();
    });
  }

  protected poser(texte: string): void {
    const question = texte.trim();
    if (!question || this.enCours()) return;
    this.question.set('');
    this.enCours.set(true);
    this.echanges.update((echanges) => [
      ...echanges,
      { question, texte: '', citations: [], sansReponse: false, enCours: true, erreur: null },
    ]);

    this.abonnement = this.service.poser(question, this.conversationId()).subscribe({
      next: (evenement) => {
        switch (evenement.nom) {
          case 'texte':
            this.modifierDernier((echange) => ({ ...echange, texte: echange.texte + evenement.donnees.texte }));
            break;
          case 'citation':
            this.modifierDernier((echange) => ({
              ...echange,
              texte: `${echange.texte} [${evenement.donnees.numero}]`,
              citations: [...echange.citations, evenement.donnees],
            }));
            break;
          case 'fin':
            // Le texte définitif du serveur fait foi : il remplace ce qui a été affiché au fil de l'eau
            this.modifierDernier((echange) => ({
              ...echange,
              texte: evenement.donnees.texte,
              sansReponse: evenement.donnees.sansReponse,
              citations: evenement.donnees.sansReponse ? [] : echange.citations,
              enCours: false,
            }));
            this.conversationId.set(evenement.donnees.conversationId);
            break;
          case 'erreur':
            this.modifierDernier((echange) => ({ ...echange, enCours: false, erreur: evenement.donnees.erreur }));
            break;
        }
      },
      error: (erreur: Error) => {
        this.modifierDernier((echange) => ({ ...echange, enCours: false, erreur: erreur.message }));
        this.enCours.set(false);
      },
      complete: () => {
        this.enCours.set(false);
        this.chargerConversations();
      },
    });
  }

  private modifierDernier(modification: (echange: Echange) => Echange): void {
    this.echanges.update((echanges) => [...echanges.slice(0, -1), modification(echanges[echanges.length - 1])]);
  }

  private chargerConversations(): void {
    this.api.conversations().subscribe((conversations) => this.conversations.set(conversations));
  }
}

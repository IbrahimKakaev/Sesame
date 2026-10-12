/**
 * QuestionsService - Pose une question et reçoit la réponse en flux
 */

import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { DemoBackend } from '../demo/demo-backend';
import { EvenementFlux } from '../modeles';
import { lireEvenements } from './flux-sse';
import { SessionService } from './session';

const MESSAGES: Record<number, string> = {
  429: 'Trop de questions en peu de temps. Réessayez dans une minute.',
  503: 'La rédaction des réponses n’est pas configurée sur ce serveur (clé d’API absente).',
};

@Injectable({ providedIn: 'root' })
export class QuestionsService {
  private readonly session = inject(SessionService);
  private readonly demo = inject(DemoBackend);

  /** Événements de la réponse ; se désabonner interrompt la requête */
  poser(question: string, conversationId: string | null): Observable<EvenementFlux> {
    return new Observable<EvenementFlux>((abonne) => {
      const interruption = new AbortController();
      const source = this.demo.actif
        ? this.demo.repondre(question, conversationId, interruption.signal)
        : this.interroger(question, conversationId, interruption.signal);
      (async () => {
        try {
          for await (const evenement of source) abonne.next(evenement);
          abonne.complete();
        } catch (erreur) {
          if (!interruption.signal.aborted) abonne.error(erreur);
        }
      })();
      return () => interruption.abort();
    });
  }

  private async *interroger(
    question: string,
    conversationId: string | null,
    signal: AbortSignal,
  ): AsyncGenerator<EvenementFlux> {
    let reponse = await this.envoyer(question, conversationId, signal);
    // Jeton d'accès expiré : un renouvellement, puis un second essai
    if (reponse.status === 401 && (await firstValueFrom(this.session.renouveler()))) {
      reponse = await this.envoyer(question, conversationId, signal);
    }
    if (!reponse.ok || !reponse.body) {
      throw new Error(MESSAGES[reponse.status] ?? 'La question n’a pas pu être traitée.');
    }
    yield* lireEvenements(reponse.body);
  }

  private envoyer(question: string, conversationId: string | null, signal: AbortSignal): Promise<Response> {
    return fetch('/api/questions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.session.jeton()}` },
      body: JSON.stringify(conversationId ? { question, conversationId } : { question }),
      signal,
    });
  }
}

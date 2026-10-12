/**
 * BandeauDemo - Changement de compte en un clic, en mode démo
 *
 * C'est la démonstration principale : poser la même question sous quatre comptes
 * aux droits différents et voir la réponse changer.
 */

import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { COMPTES_DEMO, MOT_DE_PASSE_DEMO } from '../demo/demo-backend';
import { LIBELLE_ROLE } from '../modeles';
import { SessionService } from '../services/session';

@Component({
  selector: 'app-bandeau-demo',
  template: `
    <aside class="bandeau-demo" aria-label="Mode démo">
      <p>
        <strong>Démo en ligne</strong> · sans serveur, réponses préenregistrées. Changez de compte et reposez la
        même question :
      </p>
      <div class="comptes-demo">
        @for (compte of comptes; track compte.email) {
          <button
            type="button"
            class="compte-demo"
            [class.courant]="session.profil()?.email === compte.email"
            [attr.aria-pressed]="session.profil()?.email === compte.email"
            [disabled]="enCours()"
            (click)="choisir(compte.email)"
          >
            <strong>{{ compte.nom }}</strong>
            <span>{{ libelleRole[compte.role] }} · {{ groupes(compte.groupes) }}</span>
          </button>
        }
      </div>
    </aside>
  `,
})
export class BandeauDemo {
  protected readonly session = inject(SessionService);
  private readonly routeur = inject(Router);
  protected readonly comptes = COMPTES_DEMO;
  protected readonly libelleRole = LIBELLE_ROLE;
  protected readonly enCours = signal(false);

  protected groupes(noms: string[]): string {
    return ['Tous', ...noms].join(', ');
  }

  protected choisir(email: string): void {
    this.enCours.set(true);
    this.session.connecter(email, MOT_DE_PASSE_DEMO).subscribe({
      next: () => {
        this.enCours.set(false);
        // Un compte sans droit d'administration ne reste pas sur la page d'administration
        if (this.routeur.url.includes('administration') || this.routeur.url.includes('connexion')) {
          this.routeur.navigate(['/']);
        }
      },
      error: () => this.enCours.set(false),
    });
  }
}

/**
 * App - Cadre de l'application : en-tête, navigation et bandeau du mode démo
 */

import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BandeauDemo } from './composants/bandeau-demo';
import { DemoBackend } from './demo/demo-backend';
import { LIBELLE_ROLE } from './modeles';
import { SessionService } from './services/session';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BandeauDemo],
  template: `
    @if (demo.actif) {
      <app-bandeau-demo />
    }
    <header class="entete">
      <a class="marque" routerLink="/">
        <img src="favicon.svg" alt="" width="28" height="28" />
        <span>Sésame</span>
      </a>
      @if (session.profil(); as profil) {
        <nav aria-label="Navigation principale">
          <a routerLink="/" routerLinkActive="actif" [routerLinkActiveOptions]="{ exact: true }">Questions</a>
          <a routerLink="/documents" routerLinkActive="actif">Documents</a>
          @if (session.estAdministrateur()) {
            <a routerLink="/administration" routerLinkActive="actif">Administration</a>
          }
        </nav>
        <div class="compte">
          <div>
            <strong>{{ profil.nom }}</strong>
            <span class="discret">{{ libelleRole[profil.role] }}</span>
          </div>
          <ul class="etiquettes" aria-label="Vos groupes">
            @for (groupe of profil.groupes; track groupe.id) {
              <li class="etiquette">{{ groupe.nom }}</li>
            }
          </ul>
          <button type="button" class="bouton discret" (click)="deconnecter()">Déconnexion</button>
        </div>
      }
    </header>
    <main>
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly session = inject(SessionService);
  protected readonly demo = inject(DemoBackend);
  private readonly routeur = inject(Router);
  protected readonly libelleRole = LIBELLE_ROLE;

  protected deconnecter(): void {
    this.session.deconnecter().subscribe(() => this.routeur.navigate(['/connexion']));
  }
}

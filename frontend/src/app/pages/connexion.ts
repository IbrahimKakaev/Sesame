/**
 * PageConnexion - Connexion par e-mail et mot de passe
 */

import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DemoBackend } from '../demo/demo-backend';
import { SessionService } from '../services/session';

@Component({
  selector: 'app-page-connexion',
  imports: [FormsModule],
  template: `
    <section class="carte connexion">
      <h1>Connexion</h1>
      <p class="discret">
        Posez vos questions sur les documents de l'entreprise. Les réponses citent leurs sources et ne
        s'appuient que sur les documents auxquels vous avez accès.
      </p>
      @if (demo.actif) {
        <p class="note">Mode démo : choisissez un compte dans le bandeau ci-dessus.</p>
      }
      <form (ngSubmit)="connecter()">
        <label>
          E-mail
          <input type="email" name="email" [(ngModel)]="email" autocomplete="username" required />
        </label>
        <label>
          Mot de passe
          <input type="password" name="motDePasse" [(ngModel)]="motDePasse" autocomplete="current-password" required />
        </label>
        @if (erreur(); as message) {
          <p class="erreur" role="alert">{{ message }}</p>
        }
        <button type="submit" class="bouton principal" [disabled]="enCours() || !email || !motDePasse">
          Se connecter
        </button>
      </form>
    </section>
  `,
})
export class PageConnexion {
  private readonly session = inject(SessionService);
  private readonly routeur = inject(Router);
  protected readonly demo = inject(DemoBackend);

  protected email = '';
  protected motDePasse = '';
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);

  protected connecter(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.session.connecter(this.email, this.motDePasse).subscribe({
      next: () => this.routeur.navigate(['/']),
      error: (erreur: HttpErrorResponse) => {
        this.enCours.set(false);
        this.erreur.set(
          erreur.status === 429
            ? 'Trop de tentatives. Réessayez dans quelques minutes.'
            : erreur.status === 401
              ? 'E-mail ou mot de passe incorrect.'
              : 'Connexion impossible pour le moment.',
        );
      },
    });
  }
}

/**
 * SessionService - Compte connecté et jetons
 *
 * Le jeton d'accès (10 minutes) ne vit qu'en mémoire : il disparaît au rechargement
 * de la page. Le jeton de renouvellement est dans un cookie HttpOnly que ce code ne
 * peut pas lire ; il sert à rouvrir la session au démarrage et quand le jeton d'accès
 * expire.
 */

import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';
import { Profil, Session } from '../modeles';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  private readonly etat = signal<Session | null>(null);
  // Un seul renouvellement à la fois, partagé par les requêtes qui l'attendent
  private renouvellementEnCours: Observable<boolean> | null = null;

  readonly profil = computed<Profil | null>(() => this.etat()?.utilisateur ?? null);
  readonly connecte = computed(() => this.etat() !== null);
  readonly estAdministrateur = computed(() => this.profil()?.role === 'ADMINISTRATEUR');
  readonly peutDeposer = computed(() => {
    const role = this.profil()?.role;
    return role === 'CONTRIBUTEUR' || role === 'ADMINISTRATEUR';
  });

  jeton(): string | null {
    return this.etat()?.jetonAcces ?? null;
  }

  connecter(email: string, motDePasse: string): Observable<Profil> {
    return this.http.post<Session>('/api/auth/connexion', { email, motDePasse }, { withCredentials: true }).pipe(
      tap((session) => this.etat.set(session)),
      map((session) => session.utilisateur),
    );
  }

  /** Échange le cookie de renouvellement contre un nouveau jeton d'accès */
  renouveler(): Observable<boolean> {
    if (!this.renouvellementEnCours) {
      this.renouvellementEnCours = this.http
        .post<Session>('/api/auth/renouvellement', null, { withCredentials: true })
        .pipe(
          tap((session) => this.etat.set(session)),
          map(() => true),
          catchError(() => {
            this.etat.set(null);
            return of(false);
          }),
          finalize(() => (this.renouvellementEnCours = null)),
          shareReplay(1),
        );
    }
    return this.renouvellementEnCours;
  }

  /** Au démarrage : rouvre la session si le cookie de renouvellement est encore valide */
  restaurer(): Promise<boolean> {
    return firstValueFrom(this.renouveler());
  }

  /** Relit le profil : les droits ont pu changer depuis la connexion */
  rafraichirProfil(): Observable<Profil> {
    return this.http.get<Profil>('/api/moi').pipe(
      tap((profil) => {
        const session = this.etat();
        if (session) this.etat.set({ ...session, utilisateur: profil });
      }),
    );
  }

  deconnecter(): Observable<void> {
    return this.http.post<void>('/api/auth/deconnexion', null, { withCredentials: true }).pipe(
      catchError(() => of(undefined)),
      tap(() => this.etat.set(null)),
    );
  }

  /** Session perdue (jeton refusé et renouvellement impossible) */
  oublier(): void {
    this.etat.set(null);
  }
}

import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { SessionService } from './services/session';

/** Réserve une page aux comptes connectés */
export const gardeConnecte: CanActivateFn = () =>
  inject(SessionService).connecte() || inject(Router).createUrlTree(['/connexion']);

/**
 * Réserve une page aux administrateurs. Ce n'est qu'un confort d'affichage :
 * le serveur refuse de toute façon les routes d'administration aux autres rôles.
 */
export const gardeAdministrateur: CanActivateFn = () => {
  const session = inject(SessionService);
  if (!session.connecte()) return inject(Router).createUrlTree(['/connexion']);
  return session.estAdministrateur() || inject(Router).createUrlTree(['/']);
};

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./pages/connexion').then((m) => m.PageConnexion) },
  { path: '', pathMatch: 'full', canActivate: [gardeConnecte], loadComponent: () => import('./pages/questions').then((m) => m.PageQuestions) },
  { path: 'documents', canActivate: [gardeConnecte], loadComponent: () => import('./pages/documents').then((m) => m.PageDocuments) },
  { path: 'administration', canActivate: [gardeAdministrateur], loadComponent: () => import('./pages/administration').then((m) => m.PageAdministration) },
  { path: '**', redirectTo: '' },
];

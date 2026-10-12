/**
 * jetonIntercepteur - Joint le jeton d'accès aux appels de l'API
 *
 * Sur un refus 401, tente une fois de renouveler la session puis rejoue la requête.
 * Si le renouvellement échoue, la session est oubliée et l'utilisateur renvoyé à la
 * page de connexion.
 */

import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { SessionService } from './session';

const ROUTES_SANS_JETON = ['/api/auth/'];

function avecJeton(requete: HttpRequest<unknown>, jeton: string | null): HttpRequest<unknown> {
  return jeton ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : requete;
}

export const jetonIntercepteur: HttpInterceptorFn = (requete, suite) => {
  if (!requete.url.startsWith('/api/') || ROUTES_SANS_JETON.some((route) => requete.url.startsWith(route))) {
    return suite(requete);
  }
  const session = inject(SessionService);
  const routeur = inject(Router);

  return suite(avecJeton(requete, session.jeton())).pipe(
    catchError((erreur: unknown) => {
      if (!(erreur instanceof HttpErrorResponse) || erreur.status !== 401) {
        return throwError(() => erreur);
      }
      return session.renouveler().pipe(
        switchMap((renouvele) => {
          if (!renouvele) {
            routeur.navigate(['/connexion']);
            return throwError(() => erreur);
          }
          return suite(avecJeton(requete, session.jeton()));
        }),
      );
    }),
  );
};

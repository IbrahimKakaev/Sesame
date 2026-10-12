/**
 * demoIntercepteur - Détourne les appels HTTP vers DemoBackend en mode démo
 * Hors mode démo, les requêtes partent normalement vers le serveur.
 */

import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { defer, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { DemoBackend } from './demo-backend';

export const demoIntercepteur: HttpInterceptorFn = (requete, suite) => {
  const demo = inject(DemoBackend);
  if (!demo.actif || !requete.url.startsWith('/api/')) return suite(requete);

  return defer(() => of(demo.traiter(requete))).pipe(delay(demo.latence));
};

/**
 * Outils communs aux tests : une application branchée sur le faux serveur du mode démo
 */

import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { EnvironmentProviders, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { DemoBackend, MOT_DE_PASSE_DEMO } from './demo/demo-backend';
import { demoIntercepteur } from './demo/demo.intercepteur';
import { jetonIntercepteur } from './services/jeton.intercepteur';
import { SessionService } from './services/session';

export const LEA = 'lea.martin@helianthe.example';
export const KARIM = 'karim.benali@helianthe.example';
export const INES = 'ines.roux@helianthe.example';
export const HUGO = 'hugo.lefevre@helianthe.example';

/** Fournisseurs d'une application en mode démo, sans délais artificiels */
export function fournisseursDemo(): (Provider | EnvironmentProviders)[] {
  sessionStorage.clear();
  return [provideRouter(routes), provideHttpClient(withInterceptors([jetonIntercepteur, demoIntercepteur]))];
}

/** Active le faux serveur ; à appeler après TestBed.configureTestingModule */
export function activerDemo(): DemoBackend {
  const demo = TestBed.inject(DemoBackend);
  demo.actif = true;
  demo.delaiParMot = 0;
  demo.latence = 0;
  demo.reinitialiser();
  return demo;
}

export function connecter(email: string) {
  return firstValueFrom(TestBed.inject(SessionService).connecter(email, MOT_DE_PASSE_DEMO));
}

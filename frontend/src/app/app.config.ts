import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { routes } from './app.routes';
import { demoIntercepteur } from './demo/demo.intercepteur';
import { jetonIntercepteur } from './services/jeton.intercepteur';
import { SessionService } from './services/session';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Adresses en « # » : elles survivent au rechargement sur un hébergement statique (GitHub Pages)
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([jetonIntercepteur, demoIntercepteur])),
    // La session est rouverte avant le premier affichage, pour que les gardes de route la connaissent
    provideAppInitializer(() => inject(SessionService).restaurer()),
  ],
};

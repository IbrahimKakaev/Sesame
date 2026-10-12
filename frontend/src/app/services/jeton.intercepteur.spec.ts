import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { jetonIntercepteur } from './jeton.intercepteur';
import { SessionService } from './session';

const SESSION = (jeton: string) => ({ jetonAcces: jeton, expireDans: 600, utilisateur: { id: 'u1' } });

describe('jetonIntercepteur', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  const routeur = { navigate: vi.fn() };

  beforeEach(() => {
    routeur.navigate.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jetonIntercepteur])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routeur },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).connecter('karim@exemple.test', 'secret').subscribe();
    http.expectOne('/api/auth/connexion').flush(SESSION('jeton-1'));
  });

  afterEach(() => http.verify());

  it('joint le jeton d’accès aux appels de l’API', () => {
    client.get('/api/documents').subscribe();

    const requete = http.expectOne('/api/documents');
    expect(requete.request.headers.get('Authorization')).toBe('Bearer jeton-1');
    requete.flush([]);
  });

  it('n’envoie le jeton ni aux routes d’authentification ni hors de l’API', () => {
    client.post('/api/auth/deconnexion', null).subscribe();
    client.get('https://exemple.test/autre').subscribe();

    expect(http.expectOne('/api/auth/deconnexion').request.headers.has('Authorization')).toBe(false);
    expect(http.expectOne('https://exemple.test/autre').request.headers.has('Authorization')).toBe(false);
  });

  it('sur un 401, renouvelle la session puis rejoue la requête avec le nouveau jeton', () => {
    let recu: unknown;
    client.get('/api/documents').subscribe((corps) => (recu = corps));

    http.expectOne('/api/documents').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/renouvellement').flush(SESSION('jeton-2'));
    const rejouee = http.expectOne('/api/documents');
    expect(rejouee.request.headers.get('Authorization')).toBe('Bearer jeton-2');
    rejouee.flush(['document']);

    expect(recu).toEqual(['document']);
    expect(routeur.navigate).not.toHaveBeenCalled();
  });

  it('si le renouvellement échoue, oublie la session et renvoie à la connexion', () => {
    let statut = 0;
    client.get('/api/documents').subscribe({ error: (erreur) => (statut = erreur.status) });

    http.expectOne('/api/documents').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/renouvellement').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(statut).toBe(401);
    expect(TestBed.inject(SessionService).connecte()).toBe(false);
    expect(routeur.navigate).toHaveBeenCalledWith(['/connexion']);
  });

  it('laisse passer les autres erreurs sans renouveler', () => {
    let statut = 0;
    client.get('/api/admin/journal').subscribe({ error: (erreur) => (statut = erreur.status) });

    http.expectOne('/api/admin/journal').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(statut).toBe(403);
  });
});

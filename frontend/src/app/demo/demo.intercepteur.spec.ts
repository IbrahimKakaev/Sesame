import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { DemoBackend, MOT_DE_PASSE_DEMO } from './demo-backend';
import { demoIntercepteur } from './demo.intercepteur';

describe('demoIntercepteur', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let demo: DemoBackend;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([demoIntercepteur])), provideHttpClientTesting()],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    demo = TestBed.inject(DemoBackend);
    demo.latence = 0;
    demo.reinitialiser();
  });

  afterEach(() => http.verify());

  it('hors mode démo, laisse partir les requêtes vers le serveur', () => {
    demo.actif = false;

    client.get('/api/documents').subscribe();

    http.expectOne('/api/documents').flush([]);
  });

  it('en mode démo, répond à la place du serveur sans aucune requête réseau', async () => {
    demo.actif = true;

    const session = await firstValueFrom(
      client.post<{ utilisateur: { nom: string } }>('/api/auth/connexion', {
        email: 'lea.martin@helianthe.example',
        motDePasse: MOT_DE_PASSE_DEMO,
      }),
    );
    const documents = await firstValueFrom(client.get<unknown[]>('/api/documents'));

    expect(session.utilisateur.nom).toBe('Léa Martin');
    expect(documents).toHaveLength(4);
    http.expectNone('/api/documents');
  });

  it('en mode démo, un refus arrive comme une erreur HTTP', async () => {
    demo.actif = true;

    await expect(firstValueFrom(client.get('/api/documents'))).rejects.toMatchObject({ status: 401 });
  });

  it('en mode démo, ne touche pas aux requêtes hors de l’API', () => {
    demo.actif = true;

    client.get('favicon.svg', { responseType: 'text' }).subscribe();

    http.expectOne('favicon.svg').flush('');
  });
});

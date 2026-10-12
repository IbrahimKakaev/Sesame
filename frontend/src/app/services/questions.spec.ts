import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { lastValueFrom, toArray } from 'rxjs';
import { vi } from 'vitest';
import { DemoBackend, MOT_DE_PASSE_DEMO } from '../demo/demo-backend';
import { QuestionsService } from './questions';
import { SessionService } from './session';

function reponseSse(...evenements: [string, unknown][]): Response {
  const corps = evenements.map(([nom, donnees]) => `event: ${nom}\ndata: ${JSON.stringify(donnees)}\n\n`).join('');
  return new Response(corps, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
}

describe('QuestionsService', () => {
  let service: QuestionsService;
  let http: HttpTestingController;
  const requetes = vi.fn<typeof fetch>();

  beforeEach(() => {
    sessionStorage.clear();
    requetes.mockReset();
    vi.stubGlobal('fetch', requetes);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(DemoBackend).actif = false;
    service = TestBed.inject(QuestionsService);
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).connecter('karim@exemple.test', 'secret').subscribe();
    http.expectOne('/api/auth/connexion').flush({ jetonAcces: 'jeton-1', expireDans: 600, utilisateur: { id: 'u1' } });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('envoie la question avec le jeton et rend les événements du flux', async () => {
    requetes.mockResolvedValue(
      reponseSse(['texte', { texte: 'Deux jours.' }], ['fin', { conversationId: 'c1', messageId: 'm1', texte: 'Deux jours.', sansReponse: false }]),
    );

    const evenements = await lastValueFrom(service.poser('Combien de jours ?', null).pipe(toArray()));

    expect(evenements.map((evenement) => evenement.nom)).toEqual(['texte', 'fin']);
    const [adresse, options] = requetes.mock.calls[0];
    expect(adresse).toBe('/api/questions');
    expect(options?.method).toBe('POST');
    expect((options?.headers as Record<string, string>)['Authorization']).toBe('Bearer jeton-1');
    expect(JSON.parse(options?.body as string)).toEqual({ question: 'Combien de jours ?' });
  });

  it('poursuit une conversation existante', async () => {
    requetes.mockResolvedValue(reponseSse());

    await lastValueFrom(service.poser('Et l’indemnité ?', 'c1').pipe(toArray()));

    expect(JSON.parse(requetes.mock.calls[0][1]?.body as string)).toEqual({ question: 'Et l’indemnité ?', conversationId: 'c1' });
  });

  it('sur un 401, renouvelle la session et repose la question une fois', async () => {
    requetes
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(reponseSse(['texte', { texte: 'Deux jours.' }]));

    const attente = lastValueFrom(service.poser('Combien de jours ?', null).pipe(toArray()));
    await vi.waitFor(() => http.expectOne('/api/auth/renouvellement').flush({ jetonAcces: 'jeton-2', expireDans: 600, utilisateur: { id: 'u1' } }));

    expect(await attente).toHaveLength(1);
    expect((requetes.mock.calls[1][1]?.headers as Record<string, string>)['Authorization']).toBe('Bearer jeton-2');
  });

  it.each([
    [429, 'Trop de questions en peu de temps. Réessayez dans une minute.'],
    [503, 'La rédaction des réponses n’est pas configurée sur ce serveur (clé d’API absente).'],
    [500, 'La question n’a pas pu être traitée.'],
  ])('traduit un refus %i en message lisible', async (statut, message) => {
    requetes.mockResolvedValue(new Response(null, { status: statut }));

    await expect(lastValueFrom(service.poser('Question', null))).rejects.toThrow(message);
  });

  it('en mode démo, répond sans appeler le réseau', async () => {
    const demo = TestBed.inject(DemoBackend);
    demo.actif = true;
    demo.delaiParMot = 0;
    demo.reinitialiser();
    demo.traiter(
      new (await import('@angular/common/http')).HttpRequest('POST', '/api/auth/connexion', {
        email: 'karim.benali@helianthe.example',
        motDePasse: MOT_DE_PASSE_DEMO,
      }),
    );

    const evenements = await lastValueFrom(service.poser('Quel est le salaire d’un développeur senior ?', null).pipe(toArray()));

    expect(evenements.at(-1)).toMatchObject({ nom: 'fin', donnees: { sansReponse: false } });
    expect(requetes).not.toHaveBeenCalled();
  });

  it('se désabonner interrompt la requête', async () => {
    let signal: AbortSignal | undefined;
    requetes.mockImplementation((_adresse, options) => {
      signal = options?.signal ?? undefined;
      return new Promise<Response>(() => undefined);
    });

    service.poser('Question', null).subscribe().unsubscribe();

    expect(signal?.aborted).toBe(true);
  });
});

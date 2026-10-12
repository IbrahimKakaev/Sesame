import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Profil, Session } from '../modeles';
import { SessionService } from './session';

const KARIM: Profil = {
  id: 'u1',
  email: 'karim@exemple.test',
  nom: 'Karim',
  role: 'CONTRIBUTEUR',
  actif: true,
  groupes: [{ id: 'g1', nom: 'RH', systeme: false }],
};
const SESSION: Session = { jetonAcces: 'jeton-1', expireDans: 600, utilisateur: KARIM };

describe('SessionService', () => {
  let session: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    session = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('n’a ni profil ni jeton avant la connexion', () => {
    expect(session.connecte()).toBe(false);
    expect(session.profil()).toBeNull();
    expect(session.jeton()).toBeNull();
  });

  it('garde le profil et le jeton d’accès après la connexion, sans rien écrire dans le navigateur', () => {
    session.connecter('karim@exemple.test', 'secret').subscribe();

    const requete = http.expectOne('/api/auth/connexion');
    expect(requete.request.body).toEqual({ email: 'karim@exemple.test', motDePasse: 'secret' });
    expect(requete.request.withCredentials).toBe(true);
    requete.flush(SESSION);

    expect(session.profil()).toEqual(KARIM);
    expect(session.jeton()).toBe('jeton-1');
    expect(session.peutDeposer()).toBe(true);
    expect(session.estAdministrateur()).toBe(false);
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('jeton-1');
  });

  it('un lecteur ne peut pas déposer', () => {
    session.connecter('lea@exemple.test', 'secret').subscribe();
    http.expectOne('/api/auth/connexion').flush({ ...SESSION, utilisateur: { ...KARIM, role: 'LECTEUR' } });

    expect(session.peutDeposer()).toBe(false);
  });

  it('rouvre la session au démarrage grâce au cookie de renouvellement', async () => {
    const restauration = session.restaurer();
    const requete = http.expectOne('/api/auth/renouvellement');
    expect(requete.request.withCredentials).toBe(true);
    requete.flush(SESSION);

    expect(await restauration).toBe(true);
    expect(session.connecte()).toBe(true);
  });

  it('reste déconnecté si le renouvellement est refusé', async () => {
    const restauration = session.restaurer();
    http.expectOne('/api/auth/renouvellement').flush({ erreur: 'Non authentifié' }, { status: 401, statusText: 'Unauthorized' });

    expect(await restauration).toBe(false);
    expect(session.connecte()).toBe(false);
  });

  it('ne lance qu’un renouvellement quand plusieurs requêtes l’attendent', () => {
    const resultats: boolean[] = [];
    session.renouveler().subscribe((ok) => resultats.push(ok));
    session.renouveler().subscribe((ok) => resultats.push(ok));

    http.expectOne('/api/auth/renouvellement').flush(SESSION);

    expect(resultats).toEqual([true, true]);
  });

  it('relit le profil quand les droits ont pu changer', () => {
    session.connecter('karim@exemple.test', 'secret').subscribe();
    http.expectOne('/api/auth/connexion').flush(SESSION);

    session.rafraichirProfil().subscribe();
    http.expectOne('/api/moi').flush({ ...KARIM, groupes: [] });

    expect(session.profil()?.groupes).toEqual([]);
    expect(session.jeton()).toBe('jeton-1');
  });

  it('oublie la session à la déconnexion, même si le serveur ne répond pas', () => {
    session.connecter('karim@exemple.test', 'secret').subscribe();
    http.expectOne('/api/auth/connexion').flush(SESSION);

    session.deconnecter().subscribe();
    http.expectOne('/api/auth/deconnexion').error(new ProgressEvent('error'));

    expect(session.connecte()).toBe(false);
  });
});

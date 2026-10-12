import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { DemoBackend, MOT_DE_PASSE_DEMO } from '../demo/demo-backend';
import { KARIM, activerDemo, fournisseursDemo } from '../essais';
import { SessionService } from '../services/session';
import { PageConnexion } from './connexion';

describe('PageConnexion', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDemo() });
    activerDemo();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(PageConnexion);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const saisir = async (email: string, motDePasse: string) => {
      const [champEmail, champMotDePasse] = [...page.querySelectorAll('input')];
      champEmail.value = email;
      champEmail.dispatchEvent(new Event('input'));
      champMotDePasse.value = motDePasse;
      champMotDePasse.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    const valider = async () => {
      page.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    return { fixture, page, saisir, valider };
  }

  it('le bouton reste inactif tant que le formulaire est incomplet', async () => {
    const { page, saisir } = await afficher();
    const bouton = page.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(bouton.disabled).toBe(true);

    await saisir(KARIM, MOT_DE_PASSE_DEMO);

    expect(bouton.disabled).toBe(false);
  });

  it('connecte puis ouvre la page des questions', async () => {
    const { saisir, valider } = await afficher();

    await saisir(KARIM, MOT_DE_PASSE_DEMO);
    await valider();

    expect(TestBed.inject(SessionService).profil()?.nom).toBe('Karim Benali');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('affiche un message d’erreur quand les identifiants sont refusés', async () => {
    const { page, saisir, valider } = await afficher();

    await saisir(KARIM, 'mauvais-mot-de-passe');
    await valider();

    expect(page.querySelector('[role=alert]')?.textContent).toBe('E-mail ou mot de passe incorrect.');
    expect(TestBed.inject(SessionService).connecte()).toBe(false);
  });

  it('le mot de passe est saisi dans un champ masqué', async () => {
    const { page } = await afficher();

    expect(page.querySelector('input[name=motDePasse]')?.getAttribute('type')).toBe('password');
  });

  it('signale le bandeau de comptes en mode démo seulement', async () => {
    expect((await afficher()).page.querySelector('.note')).not.toBeNull();

    TestBed.inject(DemoBackend).actif = false;
    expect((await afficher()).page.querySelector('.note')).toBeNull();
  });
});

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { App } from './app';
import { DemoBackend } from './demo/demo-backend';
import { HUGO, KARIM, activerDemo, connecter, fournisseursDemo } from './essais';
import { SessionService } from './services/session';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDemo() });
    activerDemo();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  const liens = (page: HTMLElement) => [...page.querySelectorAll('nav a')].map((lien) => lien.textContent?.trim());

  it('sans session, n’affiche ni navigation ni compte', async () => {
    const { page } = await afficher();

    expect(page.querySelector('nav')).toBeNull();
    expect(page.querySelector('.compte')).toBeNull();
  });

  it('affiche le nom, le rôle et les groupes du compte connecté', async () => {
    await connecter(KARIM);
    const { page } = await afficher();

    expect(page.querySelector('.compte')?.textContent).toContain('Karim Benali');
    expect(page.querySelector('.compte')?.textContent).toContain('Contributeur');
    expect([...page.querySelectorAll('.etiquettes li')].map((groupe) => groupe.textContent)).toEqual(['Tous', 'RH']);
    expect(liens(page)).toEqual(['Questions', 'Documents']);
  });

  it('ne montre l’administration qu’à un administrateur', async () => {
    await connecter(HUGO);

    expect(liens((await afficher()).page)).toEqual(['Questions', 'Documents', 'Administration']);
  });

  it('affiche le bandeau de changement de compte seulement en mode démo', async () => {
    expect((await afficher()).page.querySelector('app-bandeau-demo')).not.toBeNull();

    TestBed.inject(DemoBackend).actif = false;
    expect((await afficher()).page.querySelector('app-bandeau-demo')).toBeNull();
  });

  it('la déconnexion ferme la session et ramène à la page de connexion', async () => {
    await connecter(KARIM);
    const { fixture, page } = await afficher();

    [...page.querySelectorAll<HTMLButtonElement>('.compte button')].at(-1)!.click();
    await fixture.whenStable();

    expect(TestBed.inject(SessionService).connecte()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/connexion');
  });
});

import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { HUGO, KARIM, activerDemo, connecter, fournisseursDemo } from '../essais';
import { ApiService } from '../services/api';
import { PageAdministration } from './administration';

describe('PageAdministration', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: fournisseursDemo() });
    activerDemo();
    await connecter(HUGO);
  });

  async function afficher() {
    const fixture = TestBed.createComponent(PageAdministration);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const ouvrir = async (titre: string) => {
      [...page.querySelectorAll<HTMLButtonElement>('[role=tab]')].find((onglet) => onglet.textContent?.includes(titre))!.click();
      await fixture.whenStable();
    };
    const ligne = (texte: string) => [...page.querySelectorAll('tbody tr')].find((tr) => tr.textContent?.includes(texte))!;
    const caseDe = (rangee: Element, groupe: string) =>
      [...rangee.querySelectorAll('.case')].find((c) => c.textContent?.trim() === groupe)!.querySelector('input')!;
    return { fixture, page, ouvrir, ligne, caseDe };
  }

  it('liste les comptes avec leur rôle et leurs groupes', async () => {
    const { page, ligne, caseDe } = await afficher();

    expect(page.querySelectorAll('tbody tr')).toHaveLength(4);
    const karim = ligne('Karim Benali');
    expect(karim.querySelector('select')?.value).toBe('CONTRIBUTEUR');
    expect(caseDe(karim, 'RH').checked).toBe(true);
    expect(caseDe(karim, 'Finance').checked).toBe(false);
    // Le groupe commun ne se retire pas
    expect(caseDe(karim, 'Tous').disabled).toBe(true);
  });

  it('retire un compte d’un groupe : ses documents se ferment aussitôt', async () => {
    const { fixture, page, ligne, caseDe } = await afficher();

    caseDe(ligne('Karim Benali'), 'RH').click();
    await fixture.whenStable();
    ligne('Karim Benali').querySelector<HTMLButtonElement>('.bouton')!.click();
    await fixture.whenStable();

    expect(page.querySelector('.note')?.textContent).toContain('Droits de Karim Benali enregistrés');
    await connecter(KARIM);
    expect(await firstValueFrom(TestBed.inject(ApiService).documents())).toHaveLength(4);
  });

  it('affiche le refus quand l’administrateur veut se rétrograder lui-même', async () => {
    const { fixture, page, ligne } = await afficher();
    const role = ligne('Hugo Lefèvre').querySelector('select')!;

    role.value = 'LECTEUR';
    role.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    ligne('Hugo Lefèvre').querySelector<HTMLButtonElement>('.bouton')!.click();
    await fixture.whenStable();

    expect(page.querySelector('.erreur')?.textContent).toContain('Impossible de retirer ses propres droits');
    expect(ligne('Hugo Lefèvre').querySelector('select')?.value).toBe('ADMINISTRATEUR');
  });

  it('change les groupes autorisés d’un document', async () => {
    const { fixture, page, ouvrir, ligne, caseDe } = await afficher();
    await ouvrir('Droits des documents');
    expect(caseDe(ligne('Grille salariale 2026'), 'RH').checked).toBe(true);

    caseDe(ligne('Grille salariale 2026'), 'RH').click();
    await fixture.whenStable();

    expect(page.querySelector('.note')?.textContent).toContain('Droits de « Grille salariale 2026 » enregistrés');
    await connecter(KARIM);
    const visibles = await firstValueFrom(TestBed.inject(ApiService).documents());
    expect(visibles.map((document) => document.titre)).not.toContain('Grille salariale 2026');
  });

  it('crée et supprime un groupe, sans proposer de supprimer le groupe commun', async () => {
    const { fixture, page, ouvrir } = await afficher();
    await ouvrir('Groupes');
    const groupes = () => [...page.querySelectorAll('.liste .etiquette')].map((groupe) => groupe.textContent);
    expect(page.querySelectorAll('.liste .bouton')).toHaveLength(3);

    const champ = page.querySelector<HTMLInputElement>('input[name=nomGroupe]')!;
    champ.value = 'Juridique';
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(groupes()).toContain('Juridique');

    [...page.querySelectorAll('.liste li')].find((li) => li.textContent?.includes('Juridique'))!
      .querySelector<HTMLButtonElement>('.bouton')!.click();
    await fixture.whenStable();
    expect(groupes()).not.toContain('Juridique');
  });

  it('montre dans le journal qui a changé quels droits', async () => {
    const { fixture, page, ouvrir, ligne, caseDe } = await afficher();
    await ouvrir('Droits des documents');
    caseDe(ligne('Grille salariale 2026'), 'Direction').click();
    await fixture.whenStable();

    await ouvrir('Journal');

    const premiere = page.querySelector('tbody tr')!;
    expect(premiere.textContent).toContain('hugo.lefevre@helianthe.example');
    expect(premiere.textContent).toContain('DROITS_DOCUMENT_MODIFIES');
    expect(premiere.textContent).toContain('Grille salariale 2026');
  });

  it('crée un compte', async () => {
    const { fixture, page } = await afficher();
    const saisir = (nom: string, valeur: string) => {
      const champ = page.querySelector<HTMLInputElement>(`form input[name=${nom}]`)!;
      champ.value = valeur;
      champ.dispatchEvent(new Event('input'));
    };

    saisir('nom', 'Nadia Colin');
    saisir('email', 'nadia.colin@helianthe.example');
    saisir('motDePasse', 'un-mot-de-passe-long');
    await fixture.whenStable();
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(page.querySelector('.note')?.textContent).toContain('Compte nadia.colin@helianthe.example créé.');
    expect(page.querySelectorAll('tbody tr')).toHaveLength(5);
  });
});

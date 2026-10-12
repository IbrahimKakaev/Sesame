import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { SANS_REPONSE } from '../demo/demo-backend';
import { HUGO, INES, KARIM, LEA, activerDemo, connecter, fournisseursDemo } from '../essais';
import { PageQuestions } from './questions';

const SALAIRE = 'Quel est le salaire d’un développeur senior ?';

describe('PageQuestions', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDemo() });
    activerDemo();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(PageQuestions);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const poser = async (question: string) => {
      const avant = page.querySelectorAll('.echange').length;
      const champ = page.querySelector<HTMLInputElement>('#question')!;
      champ.value = question;
      champ.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      page.querySelector('form')!.dispatchEvent(new Event('submit'));
      // La réponse arrive en flux : elle est complète quand le curseur a disparu
      await vi.waitFor(async () => {
        await fixture.whenStable();
        expect(page.querySelectorAll('.echange')).toHaveLength(avant + 1);
        expect(page.querySelector('.curseur')).toBeNull();
      });
    };
    const derniereReponse = () => [...page.querySelectorAll('.echange')].at(-1)!;
    return { fixture, page, poser, derniereReponse };
  }

  it('propose les questions préenregistrées en mode démo', async () => {
    await connecter(KARIM);
    const { page } = await afficher();

    expect(page.querySelectorAll('.suggestion').length).toBeGreaterThan(8);
    expect(page.querySelector('.accueil')).not.toBeNull();
  });

  it('affiche la réponse, ses renvois et ses sources pour un compte qui a le droit', async () => {
    await connecter(KARIM);
    const { page, poser, derniereReponse } = await afficher();

    await poser(SALAIRE);

    expect(derniereReponse().querySelector('.question')?.textContent).toBe(SALAIRE);
    expect(derniereReponse().querySelector('.texte-reponse')?.textContent).toContain('52 000 à 61 000 euros');
    expect(derniereReponse().querySelectorAll('.renvoi')).toHaveLength(2);
    expect(derniereReponse().querySelector('.source')?.textContent).toContain('Grille salariale 2026');
    expect(page.querySelector<HTMLInputElement>('#question')?.value).toBe('');
  });

  it.each([LEA, INES, HUGO])('pour %s, la même question donne la réponse convenue, sans source ni chiffre', async (compte) => {
    await connecter(compte);
    const { poser, derniereReponse } = await afficher();

    await poser(SALAIRE);

    expect(derniereReponse().querySelector('.texte-reponse')?.textContent?.trim()).toBe(SANS_REPONSE);
    expect(derniereReponse().querySelector('.sources')).toBeNull();
    expect(derniereReponse().textContent).not.toContain('52 000');
  });

  it('changer de compte vide le fil : la réponse d’un autre ne reste pas à l’écran', async () => {
    await connecter(KARIM);
    const { fixture, page, poser, derniereReponse } = await afficher();
    await poser(SALAIRE);
    expect(page.textContent).toContain('52 000');

    await connecter(LEA);
    await fixture.whenStable();

    expect(page.querySelectorAll('.echange')).toHaveLength(0);
    expect(page.textContent).not.toContain('52 000');
    expect(page.querySelector('.historique')?.textContent).toContain('Aucune conversation');

    await poser(SALAIRE);
    expect(derniereReponse().textContent).toContain(SANS_REPONSE);
  });

  it('range la question dans l’historique et sait la rouvrir puis la supprimer', async () => {
    await connecter(KARIM);
    const { fixture, page, poser } = await afficher();
    await poser(SALAIRE);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(page.querySelector('.historique .lien')?.textContent).toBe(SALAIRE);
    });

    page.querySelector<HTMLButtonElement>('.historique .bouton')!.click();
    await fixture.whenStable();
    expect(page.querySelectorAll('.echange')).toHaveLength(0);

    page.querySelector<HTMLButtonElement>('.historique .lien')!.click();
    await fixture.whenStable();
    expect(page.querySelector('.echange .texte-reponse')?.textContent).toContain('52 000 à 61 000 euros');
    expect(page.querySelectorAll('.echange .renvoi')).toHaveLength(2);

    page.querySelector<HTMLButtonElement>('.historique .supprimer')!.click();
    await fixture.whenStable();
    expect(page.querySelectorAll('.echange')).toHaveLength(0);
    expect(page.querySelector('.historique')?.textContent).toContain('Aucune conversation');
  });

  it('un clic sur une suggestion pose la question', async () => {
    await connecter(INES);
    const { fixture, page } = await afficher();
    const suggestion = [...page.querySelectorAll<HTMLButtonElement>('.suggestion')].find((bouton) =>
      bouton.textContent?.includes('chiffre d\'affaires'),
    )!;

    suggestion.click();

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(page.querySelector('.echange .texte-reponse')?.textContent).toContain('9,4 millions');
      expect(page.querySelector('.curseur')).toBeNull();
    });
  });

  it('n’envoie pas une question vide', async () => {
    await connecter(KARIM);
    const { fixture, page } = await afficher();

    expect(page.querySelector<HTMLButtonElement>('.saisie button')?.disabled).toBe(true);
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(page.querySelectorAll('.echange')).toHaveLength(0);
  });
});

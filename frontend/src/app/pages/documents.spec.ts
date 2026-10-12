import { TestBed } from '@angular/core/testing';
import { HUGO, KARIM, LEA, activerDemo, connecter, fournisseursDemo } from '../essais';
import { PageDocuments } from './documents';

describe('PageDocuments', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDemo() });
    activerDemo();
  });

  async function afficher() {
    const fixture = TestBed.createComponent(PageDocuments);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const titres = () => [...page.querySelectorAll('tbody tr td:first-child')].map((cellule) => cellule.textContent);
    return { fixture, page, titres };
  }

  it('liste les seuls documents visibles par le compte, avec leur état', async () => {
    await connecter(LEA);
    const { page, titres } = await afficher();

    expect(titres()).toHaveLength(4);
    expect(titres()).not.toContain('Grille salariale 2026');
    expect(page.querySelector('.etat')?.textContent).toBe('Indexé');
  });

  it('un membre du groupe RH voit aussi les documents RH', async () => {
    await connecter(KARIM);

    expect((await afficher()).titres()).toContain('Grille salariale 2026');
  });

  it('change de liste quand le compte change', async () => {
    await connecter(KARIM);
    const { fixture, titres } = await afficher();
    expect(titres()).toHaveLength(7);

    await connecter(LEA);
    await fixture.whenStable();

    expect(titres()).toHaveLength(4);
  });

  it('ne propose pas le dépôt à un lecteur', async () => {
    await connecter(LEA);

    expect((await afficher()).page.querySelector('.depot')).toBeNull();
  });

  it('propose à un contributeur de déposer dans ses propres groupes', async () => {
    await connecter(KARIM);
    const { page } = await afficher();

    const groupes = [...page.querySelectorAll('.depot .case')].map((groupe) => groupe.textContent?.trim());
    expect(groupes).toEqual(['Tous', 'RH']);
    expect(page.querySelector<HTMLButtonElement>('.depot button[type=submit]')?.disabled).toBe(true);
  });

  it('propose à un administrateur tous les groupes', async () => {
    await connecter(HUGO);

    expect((await afficher()).page.querySelectorAll('.depot .case')).toHaveLength(4);
  });

  function choisirFichier(page: HTMLElement, fichier: File): void {
    const champ = page.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(champ, 'files', { value: [fichier], configurable: true });
    champ.dispatchEvent(new Event('change'));
  }

  it('refuse sur place un fichier de plus de 10 Mo', async () => {
    await connecter(KARIM);
    const { fixture, page } = await afficher();
    const enorme = new File(['x'], 'enorme.pdf', { type: 'application/pdf' });
    Object.defineProperty(enorme, 'size', { value: 11 * 1024 * 1024 });

    choisirFichier(page, enorme);
    await fixture.whenStable();

    expect(page.querySelector('.erreur')?.textContent).toBe('Ce fichier dépasse 10 Mo.');
    expect(page.querySelector<HTMLButtonElement>('.depot button[type=submit]')?.disabled).toBe(true);
  });

  it('affiche le refus du serveur quand le dépôt échoue', async () => {
    await connecter(KARIM);
    const { fixture, page } = await afficher();

    choisirFichier(page, new File(['%PDF-1.7'], 'note.pdf', { type: 'application/pdf' }));
    page.querySelector<HTMLInputElement>('.depot .case input')!.click();
    await fixture.whenStable();
    expect(page.querySelector<HTMLButtonElement>('.depot button[type=submit]')?.disabled).toBe(false);
    page.querySelector('.depot')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    // En démo, le faux serveur refuse tout dépôt
    expect(page.querySelector('.erreur')?.textContent).toBe('Le dépôt de fichiers est désactivé dans la démo en ligne.');
  });
});

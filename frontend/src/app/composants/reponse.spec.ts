import { TestBed } from '@angular/core/testing';
import { Citation } from '../modeles';
import { Reponse, decouperReponse } from './reponse';

const GRILLE: Citation = {
  numero: 1,
  passageId: 'p1',
  documentId: 'd1',
  titre: 'Grille salariale 2026',
  page: 1,
  extrait: 'Développeur senior : de 52 000 à 61 000 euros bruts par an.',
};
const PRIME: Citation = { ...GRILLE, numero: 2, passageId: 'p2', page: 2, extrait: 'Une prime sur objectifs.' };

describe('Reponse', () => {
  function afficher(texte: string, citations: Citation[], options: { sansReponse?: boolean; enCours?: boolean } = {}) {
    const fixture = TestBed.createComponent(Reponse);
    fixture.componentRef.setInput('texte', texte);
    fixture.componentRef.setInput('citations', citations);
    fixture.componentRef.setInput('sansReponse', options.sansReponse ?? false);
    fixture.componentRef.setInput('enCours', options.enCours ?? false);
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('découpe le texte en morceaux et renvois', () => {
    expect(decouperReponse('De 52 000 à 61 000 euros. [1] Avec une prime. [2]', [GRILLE, PRIME])).toEqual([
      { texte: 'De 52 000 à 61 000 euros. ' },
      { texte: '[1]', citation: GRILLE },
      { texte: ' Avec une prime. ' },
      { texte: '[2]', citation: PRIME },
    ]);
  });

  it('laisse en texte un renvoi dont la source n’est pas connue', () => {
    expect(decouperReponse('Réponse. [3]', [GRILLE])).toEqual([{ texte: 'Réponse. ' }, { texte: '[3]' }]);
  });

  it('affiche chaque source une seule fois, avec son document et sa page', () => {
    const { page } = afficher('Salaire. [1] Prime. [2] Encore le salaire. [1]', [GRILLE, PRIME, GRILLE]);

    expect(page.querySelectorAll('.renvoi')).toHaveLength(3);
    const sources = [...page.querySelectorAll('.titre-source')].map((source) =>
      [...source.children].map((partie) => partie.textContent?.trim()).join(' '),
    );
    expect(sources).toEqual(['1 Grille salariale 2026 page 1', '2 Grille salariale 2026 page 2']);
  });

  it('ouvre l’extrait cité au clic sur un renvoi, et le referme au second clic', () => {
    const { fixture, page } = afficher('Salaire. [1]', [GRILLE]);
    expect(page.querySelector('blockquote')).toBeNull();

    page.querySelector<HTMLButtonElement>('.renvoi')!.click();
    fixture.detectChanges();
    expect(page.querySelector('blockquote')?.textContent).toBe(GRILLE.extrait);
    expect(page.querySelector('.titre-source')?.getAttribute('aria-expanded')).toBe('true');

    page.querySelector<HTMLButtonElement>('.titre-source')!.click();
    fixture.detectChanges();
    expect(page.querySelector('blockquote')).toBeNull();
  });

  it('un renvoi annonce sa source aux lecteurs d’écran', () => {
    const { page } = afficher('Salaire. [1]', [GRILLE]);

    expect(page.querySelector('.renvoi')?.getAttribute('aria-label')).toBe('Source 1 : Grille salariale 2026, page 1');
  });

  it('affiche le texte tel quel, sans interpréter de HTML venu d’un document', () => {
    const { page } = afficher('<img src=x onerror=alert(1)> [1]', [{ ...GRILLE, extrait: '<script>alert(1)</script>' }]);
    page.querySelector<HTMLButtonElement>('.renvoi')!.click();

    expect(page.querySelector('img')).toBeNull();
    expect(page.querySelector('script')).toBeNull();
    expect(page.querySelector('.texte-reponse')?.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('signale une réponse en cours et une réponse sans source', () => {
    expect(afficher('Deux', [], { enCours: true }).page.querySelector('.curseur')).not.toBeNull();

    const { page } = afficher('Je ne trouve pas la réponse.', [], { sansReponse: true });
    expect(page.querySelector('.texte-reponse')?.classList.contains('sans-reponse')).toBe(true);
    expect(page.querySelector('.sources')).toBeNull();
  });
});

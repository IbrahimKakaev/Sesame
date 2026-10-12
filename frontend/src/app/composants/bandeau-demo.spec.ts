import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HUGO, activerDemo, connecter, fournisseursDemo } from '../essais';
import { SessionService } from '../services/session';
import { BandeauDemo } from './bandeau-demo';

describe('BandeauDemo', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDemo() });
    activerDemo();
  });

  function afficher() {
    const fixture = TestBed.createComponent(BandeauDemo);
    fixture.detectChanges();
    const boutons = [...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.compte-demo')];
    return { fixture, boutons };
  }

  it('propose les quatre comptes, avec leur rôle et leurs groupes', () => {
    const libelles = afficher().boutons.map((bouton) =>
      [...bouton.children].map((ligne) => ligne.textContent?.trim()).join(' '),
    );

    expect(libelles).toEqual([
      'Léa Martin Lecteur · Tous',
      'Karim Benali Contributeur · Tous, RH',
      'Inès Roux Contributeur · Tous, Finance',
      'Hugo Lefèvre Administrateur · Tous, Direction',
    ]);
  });

  it('connecte au compte choisi en un clic et le met en évidence', async () => {
    const { fixture, boutons } = afficher();

    boutons[1].click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(TestBed.inject(SessionService).profil()?.nom).toBe('Karim Benali');
    expect(boutons[1].classList.contains('courant')).toBe(true);
    expect(boutons[1].getAttribute('aria-pressed')).toBe('true');
    expect(boutons[0].getAttribute('aria-pressed')).toBe('false');
  });

  it('quitte la page d’administration quand on passe à un autre compte', async () => {
    await connecter(HUGO);
    const routeur = TestBed.inject(Router);
    await routeur.navigateByUrl('/administration');
    const { fixture, boutons } = afficher();

    boutons[0].click();
    await fixture.whenStable();

    expect(routeur.url).toBe('/');
  });
});

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HUGO, KARIM, activerDemo, connecter, fournisseursDemo } from './essais';

describe('routes et gardes', () => {
  let routeur: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: fournisseursDemo() });
    activerDemo();
    routeur = TestBed.inject(Router);
  });

  it.each(['/', '/documents', '/administration'])('sans session, %s renvoie à la connexion', async (adresse) => {
    await routeur.navigateByUrl(adresse);

    expect(routeur.url).toBe('/connexion');
  });

  it('un compte connecté accède aux questions et aux documents', async () => {
    await connecter(KARIM);

    await routeur.navigateByUrl('/documents');
    expect(routeur.url).toBe('/documents');
    await routeur.navigateByUrl('/');
    expect(routeur.url).toBe('/');
  });

  it('l’administration est réservée aux administrateurs', async () => {
    await connecter(KARIM);
    await routeur.navigateByUrl('/administration');
    expect(routeur.url).toBe('/');

    await connecter(HUGO);
    await routeur.navigateByUrl('/administration');
    expect(routeur.url).toBe('/administration');
  });

  it('une adresse inconnue ramène à l’accueil', async () => {
    await connecter(KARIM);
    await routeur.navigateByUrl('/n-existe-pas');

    expect(routeur.url).toBe('/');
  });
});

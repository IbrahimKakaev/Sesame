// Refait les captures d'écran du README à partir du mode démo de l'application.
// Le scénario est celui de la démonstration : la même question posée sous des
// comptes aux droits différents.
// Usage : node scripts/captures.mjs <adresse de l'application> [dossier de sortie]
//   (Playwright doit être installé : npm install playwright && npx playwright install chromium ;
//    PLAYWRIGHT peut donner le chemin du module s'il est installé ailleurs.)
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const { chromium } = await import(process.env.PLAYWRIGHT ?? 'playwright');
const adresse = process.argv[2] ?? 'http://localhost:4200';
const sortie = process.argv[3] ?? 'docs';
const QUESTION = 'Quel est le salaire d’un développeur senior ?';
mkdirSync(sortie, { recursive: true });

const navigateur = await chromium.launch();
const page = await navigateur.newPage({ viewport: { width: 1280, height: 860 }, deviceScaleFactor: 2 });
const erreurs = [];
page.on('pageerror', (erreur) => erreurs.push(erreur.message));
page.on('console', (message) => message.type() === 'error' && erreurs.push(message.text()));
const capturer = (nom) => page.screenshot({ path: join(sortie, `${nom}.png`) });

async function choisirCompte(nom) {
  await page.getByRole('button', { name: new RegExp(nom) }).click();
  await page.locator('.compte strong', { hasText: nom }).waitFor();
}

async function poser(question) {
  await page.getByLabel('Votre question').fill(question);
  const reponses = await page.locator('.echange').count();
  await page.getByRole('button', { name: 'Demander' }).click();
  await page.locator('.echange').nth(reponses).waitFor();
  // La réponse arrive en flux : le curseur disparaît quand elle est complète
  await page.locator('.curseur').waitFor({ state: 'detached', timeout: 30_000 });
}

await page.goto(`${adresse}/?demo`);
await page.getByRole('heading', { name: 'Connexion' }).waitFor();
await capturer('connexion');

// Karim, membre du groupe RH : la grille salariale lui est ouverte
await choisirCompte('Karim Benali');
await poser(QUESTION);
await page.getByRole('button', { name: /^Source 1/ }).first().click();
await capturer('reponse-avec-droits');

// Léa, lectrice sans groupe particulier : même question, aucune source autorisée
await choisirCompte('Léa Martin');
await poser(QUESTION);
await capturer('reponse-sans-droits');

await page.getByRole('link', { name: 'Documents' }).click();
await page.getByRole('heading', { name: 'Documents' }).waitFor();
await page.locator('tbody tr').first().waitFor();
await capturer('documents');

// Hugo, administrateur : il gère les droits de la grille salariale sans pouvoir la lire
await choisirCompte('Hugo Lefèvre');
await page.getByRole('link', { name: 'Questions' }).click();
await poser(QUESTION);
await page.getByRole('link', { name: 'Administration' }).click();
await page.getByRole('tab', { name: 'Droits des documents' }).click();
await page.locator('tbody tr').first().waitFor();
await capturer('administration-droits');
await page.getByRole('tab', { name: /Journal/ }).click();
// La première ligne affichée est « Journal vide » tant que les entrées ne sont pas chargées
await page.locator('tbody code').first().waitFor();
await capturer('journal');

await navigateur.close();
if (erreurs.length > 0) {
  console.error('Erreurs dans la console du navigateur :\n' + erreurs.join('\n'));
  process.exit(1);
}
console.log(`captures enregistrées dans ${sortie}/`);

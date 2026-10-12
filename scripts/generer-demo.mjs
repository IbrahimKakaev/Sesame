// Prépare les données du mode démo de l'application Angular à partir des mêmes
// sources que les PDF de démonstration : comptes et droits (demo.json), texte des
// documents (demo-donnees/sources) et réponses préenregistrées (reponses.json).
// Chaque extrait cité par une réponse doit se trouver mot pour mot dans la page
// indiquée : sinon le script échoue. La démo ne peut donc rien citer d'inventé.
// Usage : node scripts/generer-demo.mjs
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const racine = join(dirname(fileURLToPath(import.meta.url)), '..');
const sources = join(racine, 'demo-donnees/sources');
const jeu = JSON.parse(readFileSync(join(racine, 'backend/src/main/resources/demo/demo.json'), 'utf8'));
const { reponses } = JSON.parse(readFileSync(join(racine, 'demo-donnees/reponses.json'), 'utf8'));

const documents = readdirSync(sources).filter((nom) => nom.endsWith('.txt')).sort().map((nom) => {
  const cle = nom.replace('.txt', '');
  const declare = jeu.documents.find((document) => document.fichier === `${cle}.pdf`);
  if (!declare) throw new Error(`${nom} n'est pas déclaré dans demo.json`);
  const [, ...lignes] = readFileSync(join(sources, nom), 'utf8').split('\n');
  const pages = lignes.join('\n').split('=== page ===')
    .map((page) => page.split('\n').map((ligne) => ligne.trim()).filter(Boolean).join(' '));
  return { cle, titre: declare.titre, groupes: declare.groupes, pages };
});

for (const reponse of reponses) {
  for (const segment of reponse.segments) {
    const page = documents.find((document) => document.cle === segment.document)?.pages[segment.page - 1];
    if (!page?.includes(segment.extrait)) {
      throw new Error(`Extrait introuvable dans ${segment.document}, page ${segment.page} : « ${segment.extrait} »`);
    }
  }
}

const sortie = join(racine, 'frontend/src/app/demo/demo-donnees.json');
writeFileSync(sortie, JSON.stringify({
  motDePasse: jeu.motDePasse,
  groupes: ['Tous', ...jeu.groupes],
  comptes: jeu.comptes,
  documents,
  reponses,
}, null, 2) + '\n');
console.log(`${documents.length} documents, ${reponses.length} réponses -> ${sortie}`);

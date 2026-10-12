// Génère les fichiers lus par les badges du README (format « endpoint » de
// shields.io) à partir des résultats produits par la CI.
// Usage : node badges.mjs <dossier du site>
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const site = process.argv[2];
const sortie = join(site, 'badges');
mkdirSync(sortie, { recursive: true });

function ecrire(nom, label, message, color) {
  writeFileSync(join(sortie, `${nom}.json`), JSON.stringify({ schemaVersion: 1, label, message, color }));
  console.log(`${label} : ${message}`);
}

function couleur(pourcent) {
  if (pourcent >= 90) return 'brightgreen';
  if (pourcent >= 80) return 'green';
  if (pourcent >= 70) return 'yellowgreen';
  if (pourcent >= 50) return 'yellow';
  return 'orange';
}

function badgePourcent(nom, label, pourcent) {
  if (!Number.isFinite(pourcent)) throw new Error(`Valeur illisible pour ${label}`);
  ecrire(nom, label, `${Math.round(pourcent)} %`, couleur(pourcent));
}

const json = (chemin) => JSON.parse(readFileSync(chemin, 'utf8'));

// ==================== TESTS ====================

// Tests Java : un rapport Surefire par classe de test.
const rapports = 'backend/target/surefire-reports';
let testsJava = 0;
let testsEtancheite = 0;
for (const fichier of readdirSync(rapports).filter((nom) => /^TEST-.*\.xml$/.test(nom))) {
  const entete = readFileSync(join(rapports, fichier), 'utf8').match(/<testsuite\b[^>]*>/)[0];
  const attribut = (nom) => Number(entete.match(new RegExp(`\\b${nom}="(\\d+)"`))[1]);
  if (attribut('failures') + attribut('errors') > 0) throw new Error(`Tests en échec dans ${fichier}`);
  const reussis = attribut('tests') - attribut('skipped');
  testsJava += reussis;
  // Suite d'étanchéité : chaque couple utilisateur-document sur chaque route, fuites, révocation, document piégé
  if (fichier.includes('.etancheite.')) testsEtancheite += reussis;
}

// Tests Angular : nombre lu dans le compte rendu de Vitest.
const ansi = /\u001b\[[0-9;]*m/g;
const testsAngular = Number(
  readFileSync('angular-tests.log', 'utf8').replace(ansi, '').match(/Tests\s+(\d+) passed \(\1\)/)?.[1],
);
if (!testsJava || !testsEtancheite || !testsAngular) throw new Error('Nombre de tests illisible');
ecrire('tests', 'tests', `${testsJava + testsAngular} réussis`, 'brightgreen');
ecrire('etancheite', 'étanchéité des droits', `${testsEtancheite} vérifications`, 'brightgreen');

// ==================== COUVERTURE ====================

// JaCoCo : lignes couvertes et manquées par classe, colonnes LINE_MISSED et LINE_COVERED.
const [entetes, ...lignes] = readFileSync('backend/target/site/jacoco/jacoco.csv', 'utf8').trim().split('\n');
const colonnes = entetes.split(',');
const somme = (colonne) => lignes.reduce((total, ligne) => total + Number(ligne.split(',')[colonnes.indexOf(colonne)]), 0);
const couvertes = somme('LINE_COVERED');
badgePourcent('couverture-java', 'couverture Java', (100 * couvertes) / (couvertes + somme('LINE_MISSED')));
badgePourcent('couverture-angular', 'couverture Angular', json('frontend/coverage/frontend/coverage-summary.json').total.lines.pct);

// ==================== RECHERCHE ====================

// Taux de bonnes sources : questions dont le document attendu figure parmi les passages transmis au LLM.
const evaluation = json('backend/target/evaluation-recherche.json');
const { sourcesRetrouvees, taux } = evaluation.hybride;
ecrire('bonnes-sources', 'bonnes sources retrouvées', `${sourcesRetrouvees}/${evaluation.questions} (${taux} %)`, couleur(taux));

// ==================== DÉPENDANCES ====================

const NIVEAUX = [
  ['critical', 'critique', 'red'],
  ['high', 'élevée', 'orange'],
  ['moderate', 'modérée', 'yellow'],
  ['low', 'faible', 'yellowgreen'],
];

function badgeAudit(nom, label, failles, suffixe = '') {
  const presentes = NIVEAUX.filter(([niveau]) => failles[niveau] > 0);
  ecrire(
    nom,
    label,
    (presentes.length
      ? presentes.map(([niveau, libelle]) => `${failles[niveau]} ${libelle}${failles[niveau] > 1 ? 's' : ''}`).join(', ')
      : 'aucune faille') + suffixe,
    presentes.length ? presentes[0][2] : suffixe ? 'green' : 'brightgreen',
  );
}

badgeAudit('audit-npm', 'npm audit', json('audit-npm.json').metadata.vulnerabilities);

// Trivy : failles des bibliothèques embarquées dans le jar du backend. Les exceptions
// justifiées dans .trivyignore ne sont pas comptées comme failles, mais restent affichées.
const exceptions = readFileSync('.trivyignore', 'utf8').split('\n').map((ligne) => ligne.trim())
  .filter((ligne) => ligne && !ligne.startsWith('#'));
const NIVEAU_TRIVY = { CRITICAL: 'critical', HIGH: 'high', MEDIUM: 'moderate', LOW: 'low' };
const faillesJava = { critical: 0, high: 0, moderate: 0, low: 0 };
let exceptees = 0;
for (const resultat of json('audit-java.json').Results ?? []) {
  for (const faille of resultat.Vulnerabilities ?? []) {
    const niveau = NIVEAU_TRIVY[faille.Severity];
    if (exceptions.includes(faille.VulnerabilityID)) exceptees += 1;
    else if (niveau) faillesJava[niveau] += 1;
  }
}
const suffixe = exceptees > 0 ? ` · ${exceptees} exception${exceptees > 1 ? 's' : ''} justifiée${exceptees > 1 ? 's' : ''}` : '';
badgeAudit('audit-java', 'audit Java', faillesJava, suffixe);

// ==================== LIGHTHOUSE ====================

// Notes sur 100 de la démo.
const categories = json('lighthouse.report.json').categories;
for (const [nom, label, categorie] of [
  ['lighthouse-performance', 'performance', 'performance'],
  ['lighthouse-accessibilite', 'accessibilité', 'accessibility'],
  ['lighthouse-bonnes-pratiques', 'bonnes pratiques', 'best-practices'],
  ['lighthouse-seo', 'SEO', 'seo'],
]) {
  const note = Math.round(categories[categorie].score * 100);
  if (!Number.isFinite(note)) throw new Error(`Note Lighthouse illisible pour ${label}`);
  ecrire(nom, `Lighthouse · ${label}`, `${note}/100`, couleur(note));
}

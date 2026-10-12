// Sert un dossier statique sous un préfixe d'adresse, comme le fait GitHub Pages
// (https://compte.github.io/depot/). Sert à mesurer la démo avec Lighthouse dans la CI.
// Usage : node servir.mjs <dossier> <préfixe> <port>
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

const [dossier, prefixe, port] = process.argv.slice(2);
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
};

createServer((requete, reponse) => {
  const chemin = decodeURIComponent(new URL(requete.url, 'http://localhost').pathname);
  if (!chemin.startsWith(prefixe)) {
    reponse.writeHead(404).end();
    return;
  }
  // normalize écarte les « .. » : rien n'est servi hors du dossier
  const relatif = normalize(chemin.slice(prefixe.length)).replace(/^(\.\.[/\\])+/, '');
  let fichier = join(dossier, relatif);
  if (!existsSync(fichier) || statSync(fichier).isDirectory()) fichier = join(dossier, 'index.html');
  reponse.writeHead(200, { 'Content-Type': TYPES[extname(fichier)] ?? 'application/octet-stream' });
  createReadStream(fichier).pipe(reponse);
}).listen(Number(port), () => console.log(`http://localhost:${port}${prefixe}/`));

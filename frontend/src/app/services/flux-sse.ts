/**
 * Lecture d'un flux SSE (text/event-stream) reçu par fetch
 *
 * EventSource ne sait envoyer ni corps ni en-tête Authorization : la question part
 * donc par fetch, et la réponse est décodée ici, événement par événement.
 */

import { EvenementFlux } from '../modeles';

/** Décode un bloc « event: nom / data: json » ; null si le bloc est incomplet ou inconnu */
export function decoderEvenement(bloc: string): EvenementFlux | null {
  let nom = '';
  let donnees = '';
  for (const ligne of bloc.split('\n')) {
    if (ligne.startsWith('event:')) nom = ligne.slice(6).trim();
    else if (ligne.startsWith('data:')) donnees += ligne.slice(5).trim();
  }
  if (!nom || !donnees) return null;
  try {
    return { nom, donnees: JSON.parse(donnees) } as EvenementFlux;
  } catch {
    return null;
  }
}

/** Événements d'un flux, au fur et à mesure de leur arrivée */
export async function* lireEvenements(flux: ReadableStream<Uint8Array>): AsyncGenerator<EvenementFlux> {
  const lecteur = flux.getReader();
  const decodeur = new TextDecoder();
  let tampon = '';
  try {
    for (;;) {
      const { done, value } = await lecteur.read();
      tampon += decodeur.decode(value ?? new Uint8Array(), { stream: !done });
      // Un événement se termine par une ligne vide ; le reste attend la suite du flux
      let fin = tampon.indexOf('\n\n');
      while (fin >= 0) {
        const evenement = decoderEvenement(tampon.slice(0, fin));
        if (evenement) yield evenement;
        tampon = tampon.slice(fin + 2);
        fin = tampon.indexOf('\n\n');
      }
      if (done) break;
    }
  } finally {
    lecteur.releaseLock();
  }
}

import { EvenementFlux } from '../modeles';
import { decoderEvenement, lireEvenements } from './flux-sse';

function flux(...morceaux: string[]): ReadableStream<Uint8Array> {
  const encodeur = new TextEncoder();
  return new ReadableStream({
    start(controleur) {
      morceaux.forEach((morceau) => controleur.enqueue(encodeur.encode(morceau)));
      controleur.close();
    },
  });
}

async function tout(source: ReadableStream<Uint8Array>): Promise<EvenementFlux[]> {
  const evenements: EvenementFlux[] = [];
  for await (const evenement of lireEvenements(source)) evenements.push(evenement);
  return evenements;
}

describe('lecture du flux SSE', () => {
  it('décode un événement et ses données', () => {
    expect(decoderEvenement('event: texte\ndata: {"texte":"Bonjour"}')).toEqual({
      nom: 'texte',
      donnees: { texte: 'Bonjour' },
    });
  });

  it('ignore un bloc incomplet ou illisible', () => {
    expect(decoderEvenement('event: texte')).toBeNull();
    expect(decoderEvenement('data: {}')).toBeNull();
    expect(decoderEvenement('event: texte\ndata: pas du json')).toBeNull();
  });

  it('rend les événements dans l’ordre', async () => {
    const evenements = await tout(
      flux('event: texte\ndata: {"texte":"Deux jours."}\n\nevent: fin\ndata: {"texte":"Deux jours.","sansReponse":false}\n\n'),
    );

    expect(evenements.map((evenement) => evenement.nom)).toEqual(['texte', 'fin']);
  });

  it('recolle un événement coupé entre deux paquets réseau, même au milieu d’un caractère accentué', async () => {
    const octets = new TextEncoder().encode('event: texte\ndata: {"texte":"télétravail"}\n\n');
    const coupe = octets.indexOf(0xc3) + 1; // au milieu du « é »
    const source = new ReadableStream<Uint8Array>({
      start(controleur) {
        controleur.enqueue(octets.slice(0, coupe));
        controleur.enqueue(octets.slice(coupe));
        controleur.close();
      },
    });

    expect(await tout(source)).toEqual([{ nom: 'texte', donnees: { texte: 'télétravail' } }]);
  });

  it('un flux vide ne rend rien', async () => {
    expect(await tout(flux())).toEqual([]);
  });
});

/**
 * Macht aus erkannten Wörtern (mit Position im Foto) Text – Tabellen bleiben dabei Tabellen:
 * Zeilen mit mehreren Spalten werden als "Zelle | Zelle | Zelle" geschrieben, die App zeigt
 * zusammenhängende Zeilen dieser Art als Tabelle an. (Port von TabellenText.kt)
 */
export const TRENNER = ' | ';

/** Ein erkanntes Wort mit seinem Rahmen im Foto (Pixel). */
export interface Wort {
  text: string;
  links: number;
  rechts: number;
  oben: number;
  unten: number;
}

/** Spaltenabstand, ab dem zwei Wörter in verschiedene Zellen gehören (in Zeilenhöhen). */
const LUECKE = 1.2;
/** Wie weit die Mitte eines Worts von der Zeilenmitte abweichen darf (in Zeilenhöhen). */
const ZEILENTOLERANZ = 0.6;

const mitteY = (w: Wort) => (w.oben + w.unten) / 2;
const hoehe = (w: Wort) => Math.max(1, w.unten - w.oben);

export function aufbereiten(woerter: Wort[]): string {
  const sauber = woerter.filter((w) => w.text.trim() !== '');
  if (sauber.length === 0) return '';
  const h = [...sauber].map(hoehe).sort((a, b) => a - b)[Math.floor(sauber.length / 2)];

  // Zeilen bilden: von oben nach unten, Wörter mit ähnlicher Mitte gehören zusammen.
  const zeilen: Wort[][] = [];
  for (const w of [...sauber].sort((a, b) => mitteY(a) - mitteY(b))) {
    const letzte = zeilen[zeilen.length - 1];
    const mitte = letzte ? letzte.reduce((s, x) => s + mitteY(x), 0) / letzte.length : 0;
    if (letzte && Math.abs(mitteY(w) - mitte) <= h * ZEILENTOLERANZ) letzte.push(w);
    else zeilen.push([w]);
  }

  // Je Zeile in Zellen teilen, wo die Lücke zwischen zwei Wörtern groß ist.
  const zellenProZeile = zeilen.map((zeile) => {
    const zellen: string[][] = [];
    let vorher: Wort | null = null;
    for (const w of [...zeile].sort((a, b) => a.links - b.links)) {
      if (vorher === null || w.links - vorher.rechts > h * LUECKE) zellen.push([w.text]);
      else zellen[zellen.length - 1].push(w.text);
      vorher = w;
    }
    return zellen.map((z) => z.join(' '));
  });

  // Eine Tabelle gibt es erst, wenn mindestens zwei Zeilen mehrere Spalten haben.
  const mehrspaltig = zellenProZeile.filter((z) => z.length >= 2).length;
  return zellenProZeile.map((z) => (mehrspaltig >= 2 && z.length >= 2 ? z.join(TRENNER) : z.join(' '))).join('\n');
}

export type Abschnitt = { art: 'NORMAL'; text: string } | { art: 'TABELLE'; zeilen: string[][] };

const istTabellenZeile = (z: string) => z.includes(TRENNER);
const zellen = (z: string) => z.split('|').map((s) => s.trim());

/** Teilt Text in Abschnitte: normaler Text bzw. zusammenhängende Tabellenzeilen. */
export function abschnitte(text: string): Abschnitt[] {
  const out: Abschnitt[] = [];
  let normal: string[] = [];
  let tabelle: string[][] = [];
  const normalAbschliessen = () => {
    if (normal.length > 0) out.push({ art: 'NORMAL', text: normal.join('\n') });
    normal = [];
  };
  const tabelleAbschliessen = () => {
    if (tabelle.length > 0) out.push({ art: 'TABELLE', zeilen: tabelle });
    tabelle = [];
  };
  for (const zeile of text.split('\n')) {
    if (istTabellenZeile(zeile)) {
      normalAbschliessen();
      tabelle.push(zellen(zeile));
    } else {
      tabelleAbschliessen();
      normal.push(zeile);
    }
  }
  normalAbschliessen();
  tabelleAbschliessen();
  return out;
}

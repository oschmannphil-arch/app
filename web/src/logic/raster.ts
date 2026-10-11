import type { Lesson } from './models';
import { type Datum, type Min, plusTage, plusWochen, wochentag } from './zeit';
import { distinctBy } from './text';

/** Eine Unterrichtsstunde im Zeitraster der Schule. */
export interface Zeitfenster {
  stunde: number;
  beginn: Min | null;
  ende: Min | null;
}

/** Zeitraster der Schule: je Stundennummer die häufigste Beginn/Ende-Kombination. */
export function zeitraster(stunden: readonly Lesson[]): Zeitfenster[] {
  const nachNummer = new Map<number, Lesson[]>();
  for (const l of stunden) {
    if (l.stunde <= 0) continue;
    const g = nachNummer.get(l.stunde);
    if (g) g.push(l);
    else nachNummer.set(l.stunde, [l]);
  }
  const out: Zeitfenster[] = [];
  for (const [nr, ls] of nachNummer) {
    const zaehler = new Map<string, { beginn: Min; ende: Min; n: number }>();
    for (const l of ls) {
      if (l.beginn === null || l.ende === null) continue;
      const k = `${l.beginn}|${l.ende}`;
      const e = zaehler.get(k);
      if (e) e.n++;
      else zaehler.set(k, { beginn: l.beginn, ende: l.ende, n: 1 });
    }
    let beste: { beginn: Min; ende: Min; n: number } | null = null;
    for (const e of zaehler.values()) if (beste === null || e.n > beste.n) beste = e;
    out.push({ stunde: nr, beginn: beste?.beginn ?? null, ende: beste?.ende ?? null });
  }
  return out.sort((a, b) => a.stunde - b.stunde);
}

/** Nächster Schultag (Mo–Fr) in Richtung [richtung]; Feiertage und Ferien kennt die App nicht. */
export function schultagVersetzt(datum: Datum, richtung: number): Datum {
  const schritt = richtung < 0 ? -1 : 1;
  let d = plusTage(datum, schritt);
  while (wochentag(d) >= 6) d = plusTage(d, schritt);
  return d;
}

/** Heute – oder am Wochenende der kommende Montag. */
export function ersterSchultag(heute: Datum): Datum {
  return wochentag(heute) >= 6 ? schultagVersetzt(heute, 1) : heute;
}

/**
 * Ein Tag in der Woche, die angezeigt werden soll: [versatz] 0 = diese Woche (am Wochenende
 * schon die kommende), 1 = die danach, -1 = die davor.
 */
export function wochenReferenz(versatz: number, heute: Datum): Datum {
  return plusWochen(ersterSchultag(heute), versatz);
}

/** Der Montag der Woche, in der [d] liegt. */
export function montagVon(d: Datum): Datum {
  return plusTage(d, -(wochentag(d) - 1));
}

/**
 * Gruppe eines Raums zum Filtern: führende Buchstaben als Haus ("A101" → "A", "SH 1" → "SH"),
 * bei Nummern ab drei Ziffern die erste Ziffer als Etage ("204" → "Etage 2", "033" → "EG").
 */
export function raumGruppe(raum: string): string {
  const r = raum.trim();
  const buchstaben = /^\p{L}*/u.exec(r)![0];
  const rest = r.slice(buchstaben.length).trimStart();
  // Nur Buchstaben vor einer Nummer sind ein Haus ("A101", "SH 1") – "Aula" oder "Mensa" nicht.
  const haus = rest.length > 0 && /\d/.test(rest[0]) ? buchstaben : '';
  const ziffern = /^\d*/.exec(r.slice(haus.length).trimStart())![0];
  const etage = ziffern.length >= 3 ? ziffern[0] : null;
  const teile: string[] = [];
  if (haus) teile.push(haus);
  if (etage !== null) teile.push(etage === '0' ? 'EG' : `Etage ${etage}`);
  return teile.length === 0 ? 'Sonstige' : teile.join(' · ');
}

/** [1, 2, 5] → "1.–2., 5. Std" */
export function stundenListe(nummern: readonly number[]): string {
  const gruppen: Array<[number, number]> = [];
  for (const n of [...new Set(nummern)].sort((a, b) => a - b)) {
    const letzte = gruppen[gruppen.length - 1];
    if (letzte && letzte[1] + 1 === n) letzte[1] = n;
    else gruppen.push([n, n]);
  }
  return gruppen.map(([a, b]) => (a === b ? `${a}.` : `${a}.–${b}.`)).join(', ') + ' Std';
}

export { distinctBy };

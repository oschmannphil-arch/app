import { type Freiblock, freiBloecke, zusammenKey } from './freizeit';
import { jahrgang } from './freizeit';
import { type GesamtPlan, GANZE_KLASSE, type Lesson, type TagesPlan, tagesplanFuer } from './models';
import { type Zeitfenster, zeitraster } from './raster';
import { FachName } from './fachname';
import { distinctBy, hatBuchstabeOderZiffer, istBuchstabe, istBuchstabeOderZiffer, textVergleich, trimmen } from './text';
import type { Datum, Min } from './zeit';

/** Suchergebnis: ein Lehrer (Kürzel wie im Plan) oder ein Raum. */
export type Treffer = { art: 'L' | 'R'; name: string };

export const lehrer = (name: string): Treffer => ({ art: 'L', name });
export const raum = (name: string): Treffer => ({ art: 'R', name });

/** Eindeutig über Lehrer und Räume ("L:Weis", "R:204") – zum Speichern und als Listen-Key. */
export const trefferSchluessel = (t: Treffer) => `${t.art}:${t.name}`;

/** Gegenstück zu [trefferSchluessel]; null bei Unlesbarem. */
export function trefferAusSchluessel(s: string): Treffer | null {
  const name = s.slice(2);
  if (name.trim() === '') return null;
  if (s.startsWith('L:')) return lehrer(name);
  if (s.startsWith('R:')) return raum(name);
  return null;
}

export const gleicherTreffer = (a: Treffer, b: Treffer) => a.art === b.art && a.name === b.name;

/** Was ein Lehrer gerade macht bzw. was in einem Raum gerade los ist. */
export type Belegung = { art: 'JETZT'; lesson: Lesson } | { art: 'FREI'; naechste: Lesson | null };

const LEHRER_TRENNER = /[\s,;/+]+/;
const WHITESPACE = /\s+/;

/** Lehrerkürzel eines Eintrags; mehrere (Team-Teaching) stehen durch Leerzeichen o.ä. getrennt. */
export function lehrerVon(feld: string): string[] {
  return feld
    .split(LEHRER_TRENNER)
    .map((k) => trimmen(k, (c) => !istBuchstabeOderZiffer(c)))
    .filter((k) => [...k].some(istBuchstabe));
}

/**
 * Räume eines Eintrags. Mehrere Räume stehen durch Komma o.ä. getrennt oder als Nummern mit
 * Leerzeichen ("204 205"); ein Name wie "SH 1" bleibt dagegen zusammen.
 */
export function raeumeVon(feld: string): string[] {
  return feld
    .split(/[,;/]/)
    .flatMap((teil) => {
      const t = teil.trim();
      const ws = t.split(WHITESPACE).filter((w) => w !== '');
      return ws.length > 1 && ws.every((w) => /\d/.test(w)) ? ws : [t];
    })
    .filter((r) => hatBuchstabeOderZiffer(r));
}

const raumReihenfolge = (a: string, b: string) => {
  const na = /^\d+/.exec(a);
  const nb = /^\d+/.exec(b);
  const x = na ? parseInt(na[0], 10) : Number.MAX_SAFE_INTEGER;
  const y = nb ? parseInt(nb[0], 10) : Number.MAX_SAFE_INTEGER;
  return x !== y ? x - y : textVergleich(a, b);
};

const laeuft = (l: Lesson, jetzt: Min) => l.beginn !== null && l.ende !== null && jetzt >= l.beginn && jetzt < l.ende;

/**
 * Der Plan der ganzen Schule für einen Tag, aufbereitet für die Suche nach Lehrern und Räumen.
 * Grundlage ist der Schülerplan aller Klassen. Was dort nicht steht – Aufsichten, Sprechstunden,
 * Räume ohne Unterricht – kennt die App nicht; "frei" heißt also nur: laut Plan kein Unterricht.
 */
export class SchulTag {
  /** Alle Stunden aller Klassen – je Klasse so aufbereitet wie der persönliche Plan. */
  readonly stunden: Lesson[];
  /** Zeitraster: je Stundennummer die häufigste Beginn/Ende-Kombination. */
  readonly raster: Zeitfenster[];
  /** Alle Räume, die an diesem Tag im Plan vorkommen – in natürlicher Reihenfolge (2 vor 10). */
  readonly raeume: string[];
  private readonly nachLehrer: Map<string, Lesson[]>;
  private readonly nachRaum: Map<string, Lesson[]>;

  constructor(
    readonly datum: Datum,
    gesamt: GesamtPlan
  ) {
    // Ein Kurs, der in mehreren Klassenblöcken steht, kommt je Block vor – nur einmal zählen.
    this.stunden = distinctBy(
      gesamt.klassen.flatMap((kp) => tagesplanFuer(gesamt, new Set([`${kp.klasse}::${GANZE_KLASSE}`])).stunden),
      (l) => `${zusammenKey(l)}|${l.raum}|${l.entfaellt}`
    );
    this.raster = zeitraster(this.stunden);
    this.nachLehrer = this.index((l) => lehrerVon(l.lehrer));
    this.nachRaum = this.index((l) => raeumeVon(l.raum));
    this.raeume = [...this.nachRaum.keys()].sort(raumReihenfolge);
  }

  get anzahlLehrer(): number {
    return this.nachLehrer.size;
  }

  private index(schluessel: (l: Lesson) => string[]): Map<string, Lesson[]> {
    const out = new Map<string, Lesson[]>();
    for (const l of this.stunden) {
      for (const k of new Set(schluessel(l))) {
        const g = out.get(k);
        if (g) g.push(l);
        else out.set(k, [l]);
      }
    }
    for (const [k, ls] of out) out.set(k, ls.sort((a, b) => a.stunde - b.stunde || textVergleich(a.klasse, b.klasse)));
    return out;
  }

  /**
   * Lehrer und Räume, deren Name [anfrage] enthält: exakte Treffer zuerst, dann solche, die damit
   * anfangen, dann der Rest; bei Gleichstand Lehrer vor Räumen.
   */
  suche(anfrage: string, max = 30): Treffer[] {
    const q = anfrage.trim().toLowerCase();
    if (q === '') return [];
    const rang = (name: string): number | null => {
      const n = name.toLowerCase();
      if (n === q) return 0;
      if (n.startsWith(q)) return 1;
      if (n.includes(q)) return 2;
      return null;
    };
    const kandidaten: Treffer[] = [...[...this.nachLehrer.keys()].map(lehrer), ...[...this.nachRaum.keys()].map(raum)];
    const mitRang: Array<[number, Treffer]> = [];
    for (const t of kandidaten) {
      const r = rang(t.name);
      if (r !== null) mitRang.push([r, t]);
    }
    return mitRang
      .sort(
        (a, b) =>
          a[0] - b[0] ||
          (a[1].art === 'L' ? 0 : 1) - (b[1].art === 'L' ? 0 : 1) ||
          textVergleich(a[1].name.toLowerCase(), b[1].name.toLowerCase())
      )
      .slice(0, max)
      .map((x) => x[1]);
  }

  /** Alle Stunden eines Lehrers bzw. in einem Raum, nach Stunde sortiert (auch ausgefallene). */
  stundenVon(t: Treffer): Lesson[] {
    return (t.art === 'L' ? this.nachLehrer.get(t.name) : this.nachRaum.get(t.name)) ?? [];
  }

  /** Stand um [jetzt]. Ausgefallene Stunden zählen nicht – dann ist der Raum ja frei. */
  belegung(t: Treffer, jetzt: Min): Belegung {
    const aktiv = this.stundenVon(t).filter((l) => !l.entfaellt);
    const laufend = aktiv.find((l) => laeuft(l, jetzt));
    if (laufend) return { art: 'JETZT', lesson: laufend };
    const kommend = aktiv.filter((l) => l.beginn !== null && l.beginn > jetzt).sort((a, b) => a.beginn! - b.beginn!);
    return { art: 'FREI', naechste: kommend[0] ?? null };
  }

  /**
   * Räume ohne Unterricht um [jetzt] – die am längsten frei bleiben zuerst – jeweils mit der Uhrzeit,
   * ab der sie wieder belegt sind (null = heute nicht mehr). Außerhalb der Unterrichtszeit null.
   */
  freieRaeume(jetzt: Min): Array<[string, Min | null]> | null {
    const aktiv = this.stunden.filter((l) => !l.entfaellt);
    const beginne = aktiv.map((l) => l.beginn).filter((b): b is Min => b !== null);
    const enden = aktiv.map((l) => l.ende).filter((e): e is Min => e !== null);
    if (beginne.length === 0 || enden.length === 0) return null;
    if (jetzt < Math.min(...beginne) || jetzt >= Math.max(...enden)) return null;
    const frei: Array<[string, Min | null]> = [];
    for (const r of this.raeume) {
      const b = this.belegung(raum(r), jetzt);
      if (b.art === 'FREI') frei.push([r, b.naechste?.beginn ?? null]);
    }
    // Wer heute gar nicht mehr belegt ist (null), zuerst; sonst die spätere Belegung zuerst.
    return frei.sort((a, b) => {
      if (a[1] === null && b[1] === null) return 0;
      if (a[1] === null) return -1;
      if (b[1] === null) return 1;
      return b[1] - a[1];
    });
  }

  /**
   * Der Tag eines Lehrers oder Raums im Zeitraster: je Stunde die Belegung (leer = frei). Beim Lehrer
   * ohne die leeren Stunden vor der ersten und nach der letzten.
   */
  tagesablauf(t: Treffer): Array<[Zeitfenster, Lesson[]]> {
    const eigene = this.stundenVon(t);
    const nummern = [...new Set([...this.raster.map((f) => f.stunde), ...eigene.map((l) => l.stunde)])].sort((a, b) => a - b);
    let zeilen: Array<[Zeitfenster, Lesson[]]> = nummern.map((nr) => {
      const fenster =
        this.raster.find((f) => f.stunde === nr) ??
        ((l) => ({ stunde: nr, beginn: l.beginn, ende: l.ende }))(eigene.find((l) => l.stunde === nr)!);
      return [fenster, eigene.filter((l) => l.stunde === nr)];
    });
    if (t.art === 'L') {
      while (zeilen.length > 0 && zeilen[0][1].length === 0) zeilen = zeilen.slice(1);
      while (zeilen.length > 0 && zeilen[zeilen.length - 1][1].length === 0) zeilen = zeilen.slice(0, -1);
    }
    return zeilen;
  }

  /** Räume ohne Unterricht in ALLEN der Stunden [stunden] (Ausfall zählt als frei). */
  freieRaeumeIn(stunden: ReadonlySet<number>): string[] {
    if (stunden.size === 0) return [];
    return this.raeume.filter((r) => !this.stundenVon(raum(r)).some((l) => !l.entfaellt && stunden.has(l.stunde)));
  }

  /** Hat die Lehrkraft an diesem Tag Stunden im Plan, aber keine findet statt? */
  faelltGanzAus(t: Treffer): boolean {
    const alle = this.stundenVon(t);
    return alle.length > 0 && alle.every((l) => l.entfaellt);
  }

  /** Stundennummern, in denen [t] an diesem Tag Unterricht hat (ohne Ausfall). */
  unterrichtsStunden(t: Treffer): number[] {
    return [...new Set(this.stundenVon(t).filter((l) => !l.entfaellt).map((l) => l.stunde))].sort((a, b) => a - b);
  }

  /** Lücken im Tag einer Lehrkraft bzw. eines Raums: Zeiten im Zeitraster ohne Unterricht zwischen erster und letzter Stunde. */
  luecken(t: Treffer): Freiblock[] {
    const plan: TagesPlan = { kopf: { datumPlan: '', zeitstempel: '', schulnummer: '', zusatzInfo: [] }, klasse: '', stunden: this.stundenVon(t), hinweise: [] };
    return freiBloecke(plan, this.raster, true, true);
  }
}

/** Was man über eine Lehrkraft an einem Tag sieht: Fächer, Jahrgänge, Räume und Zahl der Stunden. */
export interface Steckbrief {
  faecher: string[];
  jahrgaenge: string[];
  raeume: string[];
  stunden: number;
}

/** Fasst die (nicht ausgefallenen) [stunden] einer Lehrkraft zusammen; null, wenn nichts stattfindet. */
export function steckbriefAus(stunden: readonly Lesson[]): Steckbrief | null {
  const aktiv = stunden.filter((l) => !l.entfaellt);
  if (aktiv.length === 0) return null;
  const gesammelt = (werte: string[]) => distinctBy(werte.map((w) => w.trim()).filter((w) => w !== ''), (w) => w.toLowerCase());
  const zahl = (s: string) => (/^\d+$/.test(s) ? parseInt(s, 10) : Number.MAX_SAFE_INTEGER);
  return {
    faecher: FachName.zusammen(aktiv.map((l) => l.fach)),
    jahrgaenge: gesammelt(aktiv.map((l) => jahrgang(l.klasse))).sort((a, b) => zahl(a) - zahl(b)),
    // Ein Eintrag wie "204 205" sind zwei Räume.
    raeume: gesammelt(aktiv.flatMap((l) => l.raum.split(/[ ,;/]/))).sort(),
    stunden: new Set(aktiv.map((l) => l.stunde)).size
  };
}

import { FachName } from './fachname';
import { type Datum, plusTage } from './zeit';

export type NotenArt = 'KLAUSUR' | 'MUENDLICH';
export const notenArtText = (a: NotenArt) => (a === 'KLAUSUR' ? 'Klausur' : 'Mündlich');

/**
 * Bewertungssystem: Klasse 5–10 mit Noten 1 (beste) bis 6, ab Klasse 11 (Qualifikationsphase) mit
 * Punkten 0 bis 15 (15 = beste).
 */
export type NotenSystem = 'PUNKTE' | 'NOTEN';

export const SYSTEME: Record<NotenSystem, { min: number; max: number; hoeherIstBesser: boolean }> = {
  PUNKTE: { min: 0, max: 15, hoeherIstBesser: true },
  NOTEN: { min: 1, max: 6, hoeherIstBesser: false }
};

/** Von der schlechtesten bis zur besten Wertung. */
export function schlechtZuGut(system: NotenSystem): number[] {
  const { min, max, hoeherIstBesser } = SYSTEME[system];
  const liste: number[] = [];
  for (let i = min; i <= max; i++) liste.push(i);
  return hoeherIstBesser ? liste : liste.reverse();
}

/** Klasse 11 und höher → Punkte, sonst Noten. Ohne erkennbare Klasse: Punkte. */
export const ERSTE_PUNKTE_KLASSE = 11;

/** Kurs-IDs haben die Form "Klasse::Kürzel", z.B. "12/5::D1" oder "7a::*". Die höchste Klasse entscheidet. */
export function systemAusKursIds(ids: Iterable<string>): NotenSystem {
  const klassen: number[] = [];
  for (const id of ids) {
    const k = id.split('::')[0].trim();
    const m = /^\d+/.exec(k);
    if (m) klassen.push(parseInt(m[0], 10));
  }
  if (klassen.length === 0) return 'PUNKTE';
  return Math.max(...klassen) >= ERSTE_PUNKTE_KLASSE ? 'PUNKTE' : 'NOTEN';
}

/** Eine Note: je nach [system] Punkte (0–15) oder Schulnote (1–6) in [punkte]. */
export interface Note {
  id: string;
  system: NotenSystem;
  fach: string;
  punkte: number;
  art: NotenArt;
  datum: Datum;
  notiz: string;
}

/** Durchschnitt eines Fachs: Klausur- und mündlicher Schnitt sowie das gewichtete Ergebnis. */
export interface FachSchnitt {
  fach: string;
  anzahl: number;
  klausur: number | null;
  muendlich: number | null;
  gesamt: number;
  system: NotenSystem;
}

export const MAX = 15;
export const STANDARD_KLAUSUR_ANTEIL = 50;
export const ANTEIL_MIN = 20;
export const ANTEIL_MAX = 50;

/** Kaufmännisch runden (x,5 → aufwärts), begrenzt auf den Wertebereich des Systems. */
export function runden(wert: number, system: NotenSystem = 'PUNKTE'): number {
  const { min, max } = SYSTEME[system];
  return Math.min(max, Math.max(min, Math.floor(wert + 0.5)));
}

/** Zeugniswert (Punkte bzw. Note): ab ,5 wird zur höheren Zahl gerundet. */
export const zeugnis = (f: FachSchnitt) => runden(f.gesamt, f.system);

/** Punkte als Schulnote: 15 → 1,0 · 12 → 2,0 · 9 → 3,0 · 0 → 6,0. */
export const alsSchulnote = (punkte: number) => 6 - punkte / 3;

/** Halbjahr eines Datums: August bis Januar = 1. Halbjahr, Februar bis Juli = 2. Halbjahr. */
export function halbjahr(datum: Datum): string {
  const jahr = Number(datum.slice(0, 4));
  const m = Number(datum.slice(5, 7));
  const start = m >= 8 ? jahr : jahr - 1;
  const hj = m >= 8 || m === 1 ? 1 : 2;
  return `${start}/${(start + 1) % 100} · ${hj}. HJ`;
}

/** Erster und letzter Tag des Halbjahres, in dem [datum] liegt (Aug–Jan bzw. Feb–Jul). */
export function halbjahrGrenzen(datum: Datum): [Datum, Datum] {
  const jahr = Number(datum.slice(0, 4));
  const m = Number(datum.slice(5, 7));
  if (m >= 8) return [`${jahr}-08-01`, `${jahr + 1}-01-31`];
  if (m === 1) return [`${jahr - 1}-08-01`, `${jahr}-01-31`];
  return [`${jahr}-02-01`, `${jahr}-07-31`];
}

/** Das Halbjahr davor: "2025/26 · 2. HJ" → "2025/26 · 1. HJ", "2025/26 · 1. HJ" → "2024/25 · 2. HJ". */
export function halbjahrDavor(hj: string): string | null {
  const m = /^(\d{4})\/\d{1,2} · ([12])\. HJ$/.exec(hj);
  if (!m) return null;
  const jahr = parseInt(m[1], 10);
  return m[2] === '2' ? `${jahr}/${(jahr + 1) % 100} · 1. HJ` : `${jahr - 1}/${jahr % 100} · 2. HJ`;
}

const schnitt = (werte: readonly Note[]): number | null =>
  werte.length === 0 ? null : werte.reduce((s, n) => s + n.punkte, 0) / werte.length;

/**
 * Fachschnitt mit [klausurAnteil] Prozent Gewicht für Klausuren. Gibt es nur eine Art von Noten,
 * zählt allein diese.
 */
export function fachSchnitt(noten: readonly Note[], klausurAnteil = STANDARD_KLAUSUR_ANTEIL): FachSchnitt | null {
  if (noten.length === 0) return null;
  const k = schnitt(noten.filter((n) => n.art === 'KLAUSUR'));
  const m = schnitt(noten.filter((n) => n.art === 'MUENDLICH'));
  const w = Math.min(100, Math.max(0, klausurAnteil)) / 100;
  let gesamt: number;
  if (k !== null && m !== null) gesamt = k * w + m * (1 - w);
  else if (k !== null) gesamt = k;
  else if (m !== null) gesamt = m;
  else return null;
  return { fach: noten[0].fach, anzahl: noten.length, klausur: k, muendlich: m, gesamt, system: noten[0].system };
}

/** Gleiche Fächer unabhängig von Groß-/Kleinschreibung und Kursnummer zusammenfassen. */
export const fachSchluessel = (fach: string) => FachName.schluessel(fach);

export function nachFach(noten: readonly Note[], klausurAnteil = STANDARD_KLAUSUR_ANTEIL): FachSchnitt[] {
  const gruppen = new Map<string, Note[]>();
  for (const n of noten) {
    const k = fachSchluessel(n.fach);
    const g = gruppen.get(k);
    if (g) g.push(n);
    else gruppen.set(k, [n]);
  }
  const out: FachSchnitt[] = [];
  for (const liste of gruppen.values()) {
    const f = fachSchnitt(liste, klausurAnteil);
    if (f) out.push(f);
  }
  return out.sort((a, b) => (a.fach.toLowerCase() < b.fach.toLowerCase() ? -1 : a.fach.toLowerCase() > b.fach.toLowerCase() ? 1 : 0));
}

/** Gesamtschnitt: Mittel der Fachschnitte (jedes Fach zählt gleich). */
export function gesamtSchnitt(faecher: readonly FachSchnitt[]): number | null {
  return faecher.length === 0 ? null : faecher.reduce((s, f) => s + f.gesamt, 0) / faecher.length;
}

/** Mittel der Zeugniswerte aller Fächer in allen Halbjahren (Grundlage für Block I im Abi-Rechner). */
export function durchschnittAllerHalbjahre(noten: readonly Note[], klausurAnteil = STANDARD_KLAUSUR_ANTEIL): number | null {
  const gruppen = new Map<string, Note[]>();
  for (const n of noten) {
    const k = halbjahr(n.datum);
    const g = gruppen.get(k);
    if (g) g.push(n);
    else gruppen.set(k, [n]);
  }
  const werte = [...gruppen.values()].flatMap((g) => nachFach(g, klausurAnteil)).map(zeugnis);
  return werte.length === 0 ? null : werte.reduce((a, b) => a + b, 0) / werte.length;
}

/** Was-wäre-wenn: Fachschnitt, falls als Nächstes eine weitere Note der [art] mit [wert] dazukommt. */
export function prognose(
  fach: string,
  fachNoten: readonly Note[],
  system: NotenSystem,
  art: NotenArt,
  wert: number,
  klausurAnteil = STANDARD_KLAUSUR_ANTEIL
): FachSchnitt | null {
  const { min, max } = SYSTEME[system];
  const angenommen: Note = { id: 'prognose', system, fach, punkte: Math.min(max, Math.max(min, wert)), art, datum: '1970-01-01', notiz: '' };
  return fachSchnitt([...fachNoten, angenommen], klausurAnteil);
}

/** Ist das Ziel erreicht? Punkte: mindestens, Noten: höchstens. */
export function zielErreicht(zeugnisWert: number, ziel: number, system: NotenSystem): boolean {
  return SYSTEME[system].hoeherIstBesser ? zeugnisWert >= ziel : zeugnisWert <= ziel;
}

/** Ergebnis der Frage "Was brauche ich noch?". */
export type Benoetigt = { art: 'PUNKTE'; punkte: number } | { art: 'SICHER' } | { art: 'UNMOEGLICH' };

/**
 * Welche Wertung braucht die nächste Note der [art], damit das Fach auf [ziel] kommt? Punkte:
 * Durchschnitt ab ziel − 0,5; Noten: Durchschnitt unter ziel + 0,5.
 */
export function benoetigt(
  fachNoten: readonly Note[],
  art: NotenArt,
  ziel: number,
  klausurAnteil = STANDARD_KLAUSUR_ANTEIL,
  system: NotenSystem = 'PUNKTE'
): Benoetigt {
  const w = Math.min(100, Math.max(0, klausurAnteil)) / 100;
  const dieseArt = fachNoten.filter((n) => n.art === art);
  const andere = fachNoten.filter((n) => n.art !== art);
  const summe = dieseArt.reduce((s, n) => s + n.punkte, 0);
  const n = dieseArt.length;
  const andererSchnitt = schnitt(andere);
  const gewichtDiese = art === 'KLAUSUR' ? w : 1 - w;
  const gewichtAndere = 1 - gewichtDiese;

  const gesamtMit = (x: number) => {
    const neu = (summe + x) / (n + 1);
    return andererSchnitt === null || gewichtDiese >= 1 ? neu : neu * gewichtDiese + andererSchnitt * gewichtAndere;
  };
  const erreicht = (s: number) => (SYSTEME[system].hoeherIstBesser ? s >= ziel - 0.5 : s < ziel + 0.5);

  // Anteil 0: Diese Art zählt gar nicht, das Ergebnis hängt nicht von der Note ab.
  if (gewichtDiese <= 0 && andererSchnitt !== null) return erreicht(andererSchnitt) ? { art: 'SICHER' } : { art: 'UNMOEGLICH' };
  const kandidaten = schlechtZuGut(system);
  for (let i = 0; i < kandidaten.length; i++) {
    if (erreicht(gesamtMit(kandidaten[i]))) return i === 0 ? { art: 'SICHER' } : { art: 'PUNKTE', punkte: kandidaten[i] };
  }
  return { art: 'UNMOEGLICH' };
}

/**
 * Grobe Schätzung der Abiturnote nach dem bundesweiten 900-Punkte-Schema: Block I (Qualifikationsphase)
 * höchstens 600 Punkte, Block II (fünf Prüfungen, vierfach gewertet) höchstens 300.
 */
export const Abi = {
  BLOCK_I_MAX: 600,
  BLOCK_II_MAX: 300,
  BLOCK_I_MIN: 200,
  BLOCK_II_MIN: 100,
  GESAMT_MIN: 300,

  /** Block I aus dem Durchschnitt der Kurspunkte (0–15): 15 im Schnitt = 600 Punkte. */
  blockI(schnittPunkte: number): number {
    return Math.round((Math.min(15, Math.max(0, schnittPunkte)) / 15) * 600);
  },
  /** Block II aus den Punkten der fünf Prüfungen: jede zählt vierfach. */
  blockII(pruefungen: readonly number[]): number {
    return pruefungen.reduce((s, p) => s + Math.min(15, Math.max(0, p)), 0) * 4;
  },
  bestanden(blockI: number, blockII: number): boolean {
    return blockI >= 200 && blockII >= 100 && blockI + blockII >= 300;
  },
  /** Durchschnittsnote auf eine Nachkommastelle; unter 300 Punkten gibt es keine (nicht bestanden). */
  note(gesamt: number): number | null {
    if (gesamt < 300) return null;
    const roh = (17 - gesamt / 60) / 3;
    return Math.max(1, Math.round(roh * 10) / 10);
  }
};

void plusTage;

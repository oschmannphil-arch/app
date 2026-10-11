import type { Lesson } from './models';
import { type Datum, type Min, ganzKurzesDatum, tageZwischen, uhrzeitText } from './zeit';

/** Eine selbst eingetragene Hausaufgabe. */
export interface Hausaufgabe {
  id: string;
  fach: string;
  text: string;
  /** Fälligkeitstag oder null (ohne Datum). */
  faellig: Datum | null;
  erledigt: boolean;
  erstelltAm: number;
}

/** Überfällig ist nur, was ein Datum hat, offen ist und dessen Tag vorbei ist. */
export function istUeberfaellig(h: Hausaufgabe, heute: Datum): boolean {
  return h.faellig !== null && !h.erledigt && h.faellig < heute;
}

export type PruefungsArt = 'KLAUSUR' | 'TEST';

export const pruefungsArtText = (a: PruefungsArt) => (a === 'KLAUSUR' ? 'Klausur' : 'Test');

/** Kein Ende gesetzt = ganztägig. */
export const KEIN_ENDE = -1;

/** Ein Test oder eine Klausur. */
export interface Pruefung {
  id: string;
  fach: string;
  titel: string;
  datum: Datum;
  art: PruefungsArt;
  notiz: string;
  /** Uhrzeit (Sekunden seit Mitternacht), zu der der Termin endet; [KEIN_ENDE] = ganztägig. */
  endeSekunden: number;
}

export const tageBis = (p: Pruefung, heute: Datum) => tageZwischen(heute, p.datum);

/**
 * Vorbei ist ein Termin ab dem nächsten Tag – und am selben Tag, sobald er zu Ende ist (eine Klausur
 * um 07:15 ist um 16:00 nicht mehr "heute", sondern geschrieben).
 */
export function pruefungIstVorbei(p: Pruefung, heute: Datum, jetzt: Min): boolean {
  return p.datum < heute || (p.datum === heute && p.endeSekunden !== KEIN_ENDE && jetzt * 60 >= p.endeSekunden);
}

/** Eine Klausur, die im Schulplan steht (Infotext "Klausur"). */
export interface PlanKlausur {
  datum: Datum;
  fach: string;
  kurs: string;
  klasse: string;
  stundenText: string;
  /** Ende der letzten Klausur-Stunde – danach ist die Klausur vorbei, auch am selben Tag. */
  ende: Min | null;
}

export const PLAN_PREFIX = 'plan|';

/** Stabile Kennung: derselbe Plan-Eintrag ergibt bei jedem Abruf dieselbe ID. */
export const planKlausurId = (k: PlanKlausur) => `${PLAN_PREFIX}${k.datum}|${k.klasse}|${k.kurs}|${k.fach}`;

export function planKlausurAlsPruefung(k: PlanKlausur): Pruefung {
  return {
    id: planKlausurId(k),
    fach: k.fach.trim() !== '' ? k.fach : k.kurs,
    titel: k.kurs.trim() !== '' && k.kurs.toLowerCase() !== k.fach.toLowerCase() ? k.kurs : '',
    datum: k.datum,
    art: 'KLAUSUR',
    notiz: `Aus dem Plan · ${k.stundenText}`,
    endeSekunden: k.ende === null ? KEIN_ENDE : k.ende * 60
  };
}

/** Fasst die Klausur-Stunden eines Tages zusammen (eine Klausur über mehrere Stunden = ein Eintrag). */
export function planKlausurenAusStunden(datum: Datum, stunden: readonly Lesson[]): PlanKlausur[] {
  const gruppen = new Map<string, Lesson[]>();
  for (const l of stunden) {
    if (!l.istKlausur || l.entfaellt) continue;
    const k = JSON.stringify([l.klasse, l.kursKuerzel ?? '', l.fach]);
    const g = gruppen.get(k);
    if (g) g.push(l);
    else gruppen.set(k, [l]);
  }
  const out: PlanKlausur[] = [];
  for (const gruppe of gruppen.values()) {
    const sortiert = [...gruppe].sort((a, b) => a.stunde - b.stunde);
    const erste = sortiert[0];
    const letzte = sortiert[sortiert.length - 1];
    const std = erste.stunde === letzte.stunde ? `${erste.stunde}. Std` : `${erste.stunde}.–${letzte.stunde}. Std`;
    const zeit = erste.beginn !== null && letzte.ende !== null ? ` · ${uhrzeitText(erste.beginn)}–${uhrzeitText(letzte.ende)}` : '';
    out.push({ datum, fach: erste.fach, kurs: erste.kursKuerzel ?? '', klasse: erste.klasse, stundenText: std + zeit, ende: letzte.ende });
  }
  return out;
}

/** Zusammenfassung von Hausaufgaben und nächster Klausur für die Startseite. */
export interface Uebersicht {
  offeneAufgaben: number;
  ueberfaellig: number;
  bisMorgen: number;
  naechstePruefung: Pruefung | null;
}

export function berechneUebersicht(hausaufgaben: readonly Hausaufgabe[], pruefungen: readonly Pruefung[], heute: Datum, jetzt: Min): Uebersicht {
  const offen = hausaufgaben.filter((h) => !h.erledigt);
  const morgen = tageZwischen(heute, heute) + 1;
  void morgen;
  const naechste = pruefungen
    // Nur was in den nächsten zwei Wochen ansteht – alles Weitere wäre Rauschen.
    .filter((p) => !pruefungIstVorbei(p, heute, jetzt) && tageBis(p, heute) <= 14)
    .sort((a, b) => (a.datum < b.datum ? -1 : a.datum > b.datum ? 1 : 0))[0];
  return {
    offeneAufgaben: offen.length,
    ueberfaellig: offen.filter((h) => istUeberfaellig(h, heute)).length,
    bisMorgen: offen.filter((h) => h.faellig !== null && h.faellig >= heute && tageZwischen(heute, h.faellig) <= 1).length,
    naechstePruefung: naechste ?? null
  };
}

export { ganzKurzesDatum };

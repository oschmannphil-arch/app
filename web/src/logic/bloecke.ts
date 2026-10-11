import type { Lesson } from './models';
import { stundenListe } from './raster';

/**
 * Aufeinanderfolgende Stunden desselben Unterrichts (z.B. eine Doppelstunde 1.–2.) als ein Eintrag.
 * [ersteIndex] ist die Position der ersten Stunde in der ursprünglichen Liste.
 */
export interface StundenBlock {
  ersteIndex: number;
  stunden: Lesson[];
}

export const blockErste = (b: StundenBlock) => b.stunden[0];
export const blockLetzte = (b: StundenBlock) => b.stunden[b.stunden.length - 1];

/** "1.–2. Std" bzw. "3. Std". */
export const blockStundenText = (b: StundenBlock) => stundenListe(b.stunden.map((s) => s.stunde));

/** "1.–2." bzw. "3." – für enge Zeilen. */
export const blockStundenKurz = (b: StundenBlock) => blockStundenText(b).replace(/ Std$/, '');

/** Eine Stunde für den ganzen Block: Beginn der ersten, Ende der letzten. */
export function blockZusammengefasst(b: StundenBlock): Lesson {
  if (b.stunden.length === 1) return blockErste(b);
  return { ...blockErste(b), ende: blockLetzte(b).ende, hatAufgaben: b.stunden.some((s) => s.hatAufgaben) };
}

function schliesstAn(a: Lesson, b: Lesson, maxPauseMinuten: number): boolean {
  if (b.stunde !== a.stunde + 1) return false;
  // Eine Klausur ist ein einziger Termin: aufeinanderfolgende Klausurstunden desselben Kurses gehören
  // zusammen – auch über Pausen hinweg und egal, wie der Plan den Hinweis je Stunde formuliert.
  if (a.istKlausur && b.istKlausur) {
    return a.klasse === b.klasse && a.kursKuerzel === b.kursKuerzel && a.fach === b.fach && a.lehrer === b.lehrer && a.raum === b.raum;
  }
  if (a.klasse !== b.klasse || a.kursKuerzel !== b.kursKuerzel || a.fach !== b.fach) return false;
  if (a.lehrer !== b.lehrer || a.raum !== b.raum || a.info !== b.info) return false;
  if (a.entfaellt !== b.entfaellt || a.status !== b.status || a.istKlausur !== b.istKlausur) return false;
  if (a.ende !== null && b.beginn !== null) {
    const pause = b.beginn - a.ende;
    if (pause < 0 || pause > maxPauseMinuten) return false;
  }
  return true;
}

/**
 * Fasst Stunden zusammen, die direkt aufeinander folgen und sonst gleich sind (Kurs, Lehrkraft, Raum,
 * Status, Hinweis). Pausen bis [maxPauseMinuten] trennen nicht – eine Doppelstunde über die große
 * Pause hinweg ist keine, aber 5 Minuten zwischen 1. und 2. Stunde schon. Parallele Kurse (gleiche
 * Stundennummer) werden einzeln verfolgt. Klausurstunden eines Kurses werden immer zu einem Block.
 */
export function alsBloecke(stunden: readonly Lesson[], maxPauseMinuten = 10): StundenBlock[] {
  const bloecke: Lesson[][] = [];
  const erste: number[] = [];
  stunden.forEach((l, index) => {
    let ziel = -1;
    for (let i = bloecke.length - 1; i >= 0; i--) {
      const b = bloecke[i];
      if (schliesstAn(b[b.length - 1], l, maxPauseMinuten)) {
        ziel = i;
        break;
      }
    }
    if (ziel >= 0) {
      bloecke[ziel].push(l);
    } else {
      bloecke.push([l]);
      erste.push(index);
    }
  });
  return bloecke.map((s, i) => ({ ersteIndex: erste[i], stunden: s }));
}

/** Für die Anzeige: als Blöcke ([bloecke] = true) oder jede Stunde als eigener Eintrag. */
export function alsEintraege(stunden: readonly Lesson[], bloecke: boolean): StundenBlock[] {
  return bloecke ? alsBloecke(stunden) : stunden.map((l, i) => ({ ersteIndex: i, stunden: [l] }));
}

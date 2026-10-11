import type { Lesson } from './models';
import { KURS_MUSTER, SATZ_GRENZE, hatBuchstabeOderZiffer, woerter } from './text';

/** Wendungen, mit denen die Schule einen Ausfall meldet – eine Liste für Parser, Klausur-Logik und Hinweise. */
export const AUSFALL_PHRASEN = ['fällt aus', 'faellt aus', 'entfällt', 'entfaellt'];

/** Meldet dieser Satz einen Ausfall ("BIO3 Herr X fällt aus")? */
export function nenntAusfall(satz: string): boolean {
  const s = satz.toLowerCase();
  return AUSFALL_PHRASEN.some((p) => s.includes(p));
}

/**
 * Der Hinweis der Schule zu dieser Stunde, aufs Wesentliche gekürzt. An Klausurtagen hängt
 * die Schule an jede Stunde die ganze Liste ("Klausur!; BIO3 … fällt aus; CHE1 … fällt aus; …").
 * Ausfälle anderer Kurse gehen dich nichts an und fliegen raus; was dich betrifft
 * ("Klausur!", Ausfall deines eigenen Kurses, allgemeine Hinweise) bleibt.
 */
export function hinweisKurz(l: Lesson): string {
  const eigene = new Set(
    [l.kursKuerzel, l.fach]
      .filter((k): k is string => k !== null && k !== undefined)
      .map((k) => k.trim().toLowerCase())
      .filter(hatBuchstabeOderZiffer)
  );
  const saetze = l.info
    .split(SATZ_GRENZE)
    .map((s) => s.trim())
    .filter((s) => s !== '')
    .filter((satz) => {
      const ws = woerter(satz).map((w) => w.toLowerCase());
      // Eine Klausurstunde "fällt" nicht aus – Ausfall-Zeilen zu Kursen wären dort nur verwirrend.
      return !nenntAusfall(satz) || (!l.istKlausur && ws.some((w) => eigene.has(w))) || !KURS_MUSTER.test(satz);
    });
  return [...new Set(saetze)].join('; ');
}

/** Name zum Anzeigen: das Fach, sonst das Kurskürzel; null, wenn der Plan beides leer lässt ("---"). */
export function anzeigeName(l: Lesson): string | null {
  if (hatBuchstabeOderZiffer(l.fach)) return l.fach;
  if (l.kursKuerzel && l.kursKuerzel.trim() !== '') return l.kursKuerzel;
  return null;
}

import { anzeigeName } from './hinweis';
import type { Lesson } from './models';
import type { Datum } from './zeit';

export type AenderungsArt = 'ENTFALL' | 'VERTRETUNG' | 'RAUM';

/** Eine Änderung an einer deiner Stunden (Ausfall, Vertretung, anderer Raum). */
export interface Aenderung {
  lesson: Lesson;
  art: AenderungsArt;
  /** Enthält auch den neuen Stand: Wechselt der Raum ein zweites Mal, ist das eine neue Änderung. */
  schluessel: string;
}

const hatInhalt = (s: string) => /[\p{L}\p{N}]/u.test(s);

function kennung(l: Lesson): string {
  return [l.klasse, String(l.stunde), l.kursKuerzel ?? '', l.unterrichtsNr ?? ''].join('|');
}

function neu(l: Lesson, art: AenderungsArt): Aenderung {
  const stand = art === 'ENTFALL' ? 'E' : art === 'VERTRETUNG' ? `V:${l.lehrer}` : `R:${l.raum}`;
  return { lesson: l, art, schluessel: `${kennung(l)}#${stand}` };
}

/**
 * Alle aktuellen Änderungen eines Tages. Bei einer Klausur ist ein anderer Lehrer nur die Aufsicht
 * und keine Vertretung; Platzhalter wie "---" sind kein neuer Lehrer bzw. Raum.
 */
export function aenderungenVon(stunden: Lesson[]): Aenderung[] {
  const out: Aenderung[] = [];
  for (const l of stunden) {
    if (l.entfaellt) {
      out.push(neu(l, 'ENTFALL'));
      continue;
    }
    if (l.lehrerGeaendert && !l.istKlausur && hatInhalt(l.lehrer)) out.push(neu(l, 'VERTRETUNG'));
    if (l.raumGeaendert && hatInhalt(l.raum)) out.push(neu(l, 'RAUM'));
  }
  return out;
}

const ART_REIHE: Record<AenderungsArt, number> = { ENTFALL: 0, VERTRETUNG: 1, RAUM: 2 };

function kurzerHinweis(l: Lesson): string | null {
  return l.info.trim() !== '' && l.info.length <= 60 && !l.info.includes(';') ? l.info : null;
}

export function aenderungsZeile(a: Aenderung): string {
  const l = a.lesson;
  const name = anzeigeName(l);
  const kopf = `${l.stunde}. Std${name ? ' ' + name : ''}`;
  if (a.art === 'ENTFALL') {
    const h = kurzerHinweis(l);
    return `${kopf}: fällt aus${h ? ` (${h})` : ''}`;
  }
  if (a.art === 'VERTRETUNG') return `${kopf}: Vertretung bei ${l.lehrer}${l.originalTeacher ? ` statt ${l.originalTeacher}` : ''}`;
  return `${kopf}: Raum ${l.raum}${l.originalRoom ? ` statt ${l.originalRoom}` : ''}`;
}

export function aenderungsText(alle: Aenderung[]): string {
  return [...alle]
    .sort((a, b) => a.lesson.stunde - b.lesson.stunde || ART_REIHE[a.art] - ART_REIHE[b.art])
    .map(aenderungsZeile)
    .join('\n');
}

/**
 * Stand der Änderungen eines Tages über Kalendertage hinweg: [basis] ist der Stand vom letzten Tag
 * davor (null = noch kein Vergleich möglich), [neu] der zuletzt gesehene Stand von [neuTag].
 */
export interface VerlaufsStand {
  basis: string[] | null;
  neu: string[];
  neuTag: Datum;
}

/** Ein neuer Kalendertag macht den zuletzt gesehenen Stand zur Basis; am selben Tag wird nur ersetzt. */
export function fortschreiben(alt: VerlaufsStand | undefined, aktuell: string[], heute: Datum): VerlaufsStand {
  if (!alt) return { basis: null, neu: aktuell, neuTag: heute };
  if (alt.neuTag < heute) return { basis: alt.neu, neu: aktuell, neuTag: heute };
  return { ...alt, neu: aktuell };
}

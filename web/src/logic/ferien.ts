import { type Datum, ganzKurzesDatum, plusTage, tageZwischen, wochentag } from './zeit';

/** Ein Ferienabschnitt; [bis] ist der letzte freie Tag. */
export interface Ferienabschnitt {
  name: string;
  von: Datum;
  bis: Datum;
}

/**
 * Schulferien Sachsen-Anhalt als Countdown. Die Termine stammen aus öffentlichen Ferienkalendern und
 * sind ohne Gewähr – die Quellen weichen teils voneinander ab, verbindlich ist die Schule.
 */
export const FERIEN: Ferienabschnitt[] = [
  { name: 'Herbstferien', von: '2026-10-19', bis: '2026-10-30' },
  { name: 'Weihnachtsferien', von: '2026-12-21', bis: '2027-01-02' },
  { name: 'Winterferien', von: '2027-02-01', bis: '2027-02-06' },
  { name: 'Osterferien', von: '2027-03-22', bis: '2027-03-27' },
  { name: 'Sommerferien', von: '2027-07-10', bis: '2027-08-20' }
];

/** Wie weit im Voraus der Countdown gezeigt wird. */
export const VORLAUF_TAGE = 60;

export function laufendeFerien(heute: Datum, liste = FERIEN): Ferienabschnitt | null {
  return liste.find((f) => heute >= f.von && heute <= f.bis) ?? null;
}

export function naechsteFerien(heute: Datum, liste = FERIEN): Ferienabschnitt | null {
  return liste.filter((f) => f.von > heute).sort((a, b) => (a.von < b.von ? -1 : 1))[0] ?? null;
}

/** Text für die Startseite oder null, wenn nichts Ferienbezogenes ansteht. */
export function ferienText(heute: Datum, liste = FERIEN): string | null {
  const laufend = laufendeFerien(heute, liste);
  if (laufend) return `${laufend.name}: noch bis ${ganzKurzesDatum(laufend.bis)}`;
  const n = naechsteFerien(heute, liste);
  if (!n) return null;
  // Gezählt wird bis zum letzten Schultag (meist Freitag), nicht bis zum Wochenende davor.
  const letzter = letzterSchultagVor(n.von);
  const tage = tageZwischen(heute, letzter);
  if (tage > VORLAUF_TAGE) return null;
  if (tage < 0) return tageZwischen(heute, n.von) === 1 ? `Morgen beginnen die ${n.name}` : `Die ${n.name} beginnen am ${ganzKurzesDatum(n.von)}`;
  if (tage === 0) return `Heute ist der letzte Schultag vor den ${n.name}`;
  if (tage === 1) return `Morgen ist der letzte Schultag vor den ${n.name}`;
  return `Noch ${tage} Tage bis zu den ${n.name} (letzter Schultag: ${ganzKurzesDatum(letzter)})`;
}

/** Der letzte Tag Montag bis Freitag vor [ferienbeginn]. */
export function letzterSchultagVor(ferienbeginn: Datum): Datum {
  let d = plusTage(ferienbeginn, -1);
  while (wochentag(d) >= 6) d = plusTage(d, -1);
  return d;
}

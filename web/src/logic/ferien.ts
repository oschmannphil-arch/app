import { type Datum, ganzKurzesDatum, tageZwischen } from './zeit';

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
  const tage = tageZwischen(heute, n.von);
  if (tage > VORLAUF_TAGE) return null;
  if (tage === 1) return `Morgen beginnen die ${n.name}`;
  return `Noch ${tage} Tage bis zu den ${n.name} (${ganzKurzesDatum(n.von)})`;
}

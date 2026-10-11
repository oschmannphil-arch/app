import { GANZE_KLASSE, type KursInfo, istGanzeKlasse, kursId } from './models';

/**
 * Prüft die gewählten Kurse gegen die aktuelle Kursliste der Schule (z. B. nach dem Halbjahreswechsel):
 * Kurse, die es nicht mehr gibt, werden gemeldet; steht ein eindeutiger Nachfolger bereit (gleiche
 * Buchstaben im Kürzel, gleiche Klasse), wird er vorgeschlagen.
 */
export interface MigrationErgebnis {
  /** Alte Kurs-ID → neue Kurs-ID, wenn es genau einen passenden Nachfolger gibt. */
  ersetzt: Record<string, string>;
  /** Kurse, die es nicht mehr gibt und für die kein eindeutiger Nachfolger gefunden wurde. */
  fehlend: string[];
}

export const hatAenderung = (e: MigrationErgebnis) => Object.keys(e.ersetzt).length > 0 || e.fehlend.length > 0;

/** Nur die Nachfolger einsetzen; fehlende Kurse bleiben stehen, bis man sie selbst abwählt. */
export function mitNachfolgern(gewaehlt: readonly string[], e: MigrationErgebnis): string[] {
  return [...gewaehlt.filter((k) => !(k in e.ersetzt)), ...Object.values(e.ersetzt)];
}

const klasseVon = (id: string) => id.split('::')[0];
const kuerzelVon = (id: string) => (id.includes('::') ? id.slice(id.indexOf('::') + 2) : '');
const buchstaben = (k: string) => k.replace(/\d/g, '').toLowerCase();

export function pruefeKurse(gewaehlt: readonly string[], verfuegbar: readonly KursInfo[]): MigrationErgebnis {
  if (verfuegbar.length === 0) return { ersetzt: {}, fehlend: [] };
  const ids = new Set(verfuegbar.map(kursId));
  const klassen = new Set(verfuegbar.map((k) => k.klasse));
  const gewaehltSet = new Set(gewaehlt);
  const ersetzt: Record<string, string> = {};
  const fehlend: string[] = [];
  for (const alt of [...gewaehlt].sort()) {
    if (ids.has(alt)) continue;
    // Ganze Klasse oder eine Klasse, die in der Liste gar nicht vorkommt: nichts Sicheres zu sagen.
    if (kuerzelVon(alt) === GANZE_KLASSE || !klassen.has(klasseVon(alt))) continue;
    const kandidaten = verfuegbar.filter(
      (k) =>
        !istGanzeKlasse(k) &&
        !gewaehltSet.has(kursId(k)) &&
        buchstaben(k.kuerzel) === buchstaben(kuerzelVon(alt)) &&
        buchstaben(k.kuerzel) !== '' &&
        k.klasse === klasseVon(alt)
    );
    if (kandidaten.length === 1) ersetzt[alt] = kursId(kandidaten[0]);
    else fehlend.push(alt);
  }
  return { ersetzt, fehlend };
}

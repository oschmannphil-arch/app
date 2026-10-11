import { distinctBy, gruppieren } from './text';

/**
 * Oberstufenkurse heißen oft "GEO2" oder "Eng1", das normale Fach "Geo" – gemeint ist dasselbe Fach.
 * Für Gruppieren, Farben und Listen zählt darum nur das erste Wort aus Buchstaben, klein geschrieben.
 */
export const FachName = {
  /** "GEO2" → "geo", "Geo" → "geo", "spo2_3JU" → "spo". */
  schluessel(fach: string): string {
    const m = /[a-zäöüß]+/.exec(fach.toLowerCase());
    return m ? m[0] : fach.trim().toLowerCase();
  },

  /**
   * Fasst Schreibweisen desselben Fachs zusammen. Angezeigt wird die Variante ohne Ziffern ("Geo"),
   * sonst die erste. Leere Namen und Namen ohne Buchstaben fallen weg; sortiert nach Name.
   */
  zusammen(namen: readonly string[]): string[] {
    const bereinigt = namen.map((n) => n.trim()).filter((n) => /\p{L}/u.test(n));
    const gruppen = gruppieren(bereinigt, (n) => FachName.schluessel(n));
    return [...gruppen.values()]
      .map((g) => g.find((n) => !/\d/.test(n)) ?? g[0])
      .sort((a, b) => (a.toLowerCase() < b.toLowerCase() ? -1 : a.toLowerCase() > b.toLowerCase() ? 1 : 0));
  }
};

void distinctBy;

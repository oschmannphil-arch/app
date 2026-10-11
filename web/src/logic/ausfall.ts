import { FachName } from './fachname';
import type { Lesson } from './models';
import { type Datum, tageZwischen } from './zeit';
import { gruppieren, hatBuchstabe } from './text';

/** Ergebnis der Ausfall-Statistik. */
export interface AusfallErgebnis {
  tage: number;
  stundenGesamt: number;
  ausgefallen: number;
  kommend: number;
  proFach: Array<[string, number]>;
}

export const anteilProzent = (e: AusfallErgebnis) => (e.stundenGesamt === 0 ? 0 : Math.round((e.ausgefallen * 100) / e.stundenGesamt));

/**
 * Name des Fachs einer ausgefallenen Stunde: Bei Ausfall steht im Fach oft nur "---" oder nichts –
 * dann das Kurskürzel, sonst das erste Wort aus dem Hinweis ("PHY1 fällt aus" → "PHY1").
 */
export function ausfallName(l: Lesson): string {
  if (hatBuchstabe(l.fach)) return l.fach.trim();
  if (l.kursKuerzel && hatBuchstabe(l.kursKuerzel)) return l.kursKuerzel.trim();
  const wort = l.info.split(/[ \n,:]/).find((w) => hatBuchstabe(w) && /\d/.test(w));
  return wort?.trim() ?? '';
}

/** "n;stunde|fach,stunde|fach": n = Stunden des Tages, danach die ausgefallenen (ohne Klausuren). */
export function kodieren(stunden: readonly Lesson[]): string {
  const echte = stunden.filter((l) => l.fach.trim() !== '' || l.entfaellt);
  const gesamt = new Set(echte.map((l) => l.stunde)).size;
  const gesehen = new Set<number>();
  const aus = echte.filter((l) => {
    if (!l.entfaellt || l.istKlausur || gesehen.has(l.stunde)) return false;
    gesehen.add(l.stunde);
    return true;
  });
  return `${gesamt};` + aus.map((l) => `${l.stunde}|${ausfallName(l).replace(/[,|]/g, ' ')}`).join(',');
}

/** Auswertung für [von]..[bis] (einschließlich); Tage nach [heute] zählen als "kommend". */
export function auswerten(eintraege: Record<string, string>, von: Datum, bis: Datum, heute: Datum): AusfallErgebnis {
  let tage = 0;
  let gesamt = 0;
  let ausgefallen = 0;
  let kommend = 0;
  const zaehler = new Map<string, number>();
  const namen = new Map<string, string[]>();
  for (const [datum, wert] of Object.entries(eintraege)) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(datum) || datum < von || datum > bis) continue;
    const i = wert.indexOf(';');
    if (i < 0) continue;
    const n = parseInt(wert.slice(0, i), 10);
    if (Number.isNaN(n)) continue;
    const aus = wert.slice(i + 1).split(',').filter((e) => e.trim() !== '');
    if (datum > heute) {
      kommend += aus.length;
      continue;
    }
    tage++;
    gesamt += n;
    ausgefallen += aus.length;
    for (const e of aus) {
      const fach = e.includes('|') ? e.slice(e.indexOf('|') + 1) : '';
      const name = fach.trim() === '' ? 'Unbekannt' : fach;
      const k = FachName.schluessel(name);
      zaehler.set(k, (zaehler.get(k) ?? 0) + 1);
      namen.set(k, [...(namen.get(k) ?? []), name]);
    }
  }
  const proFach = [...zaehler.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([k, n]): [string, number] => [FachName.zusammen(namen.get(k) ?? [])[0] ?? 'Unbekannt', n]);
  return { tage, stundenGesamt: gesamt, ausgefallen, kommend, proFach };
}

void gruppieren;
void tageZwischen;

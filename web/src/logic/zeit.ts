/**
 * Zeit und Datum ohne Zeitzonen-Ärger: Uhrzeiten sind Minuten seit Mitternacht, Tage sind
 * "JJJJ-MM-TT"-Texte (sortierbar, als Schlüssel brauchbar).
 */

/** Minuten seit Mitternacht. */
export type Min = number;

/** Ein Tag als "JJJJ-MM-TT". */
export type Datum = string;

export const TAG_MINUTEN = 24 * 60;

/** "7:15" oder "07:15" → 435; sonst null. */
export function parseUhrzeit(text: string): Min | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(text.trim());
  if (!m) return null;
  const h = Number(m[1]);
  const min = Number(m[2]);
  if (h > 23 || min > 59) return null;
  return h * 60 + min;
}

/** 435 → "07:15". */
export function uhrzeitText(m: Min): string {
  const h = Math.floor(m / 60);
  const min = m % 60;
  return `${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`;
}

const MS_PRO_TAG = 86_400_000;

/** Tage seit 1970-01-01. */
export function epochTag(d: Datum): number {
  const [j, m, t] = d.split('-').map(Number);
  return Math.round(Date.UTC(j, m - 1, t) / MS_PRO_TAG);
}

export function ausEpochTag(n: number): Datum {
  const dt = new Date(n * MS_PRO_TAG);
  return `${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, '0')}-${String(dt.getUTCDate()).padStart(2, '0')}`;
}

export function plusTage(d: Datum, n: number): Datum {
  return ausEpochTag(epochTag(d) + n);
}

export function plusWochen(d: Datum, n: number): Datum {
  return plusTage(d, n * 7);
}

/** Tage von [von] bis [bis] (negativ, wenn bis früher liegt). */
export function tageZwischen(von: Datum, bis: Datum): number {
  return epochTag(bis) - epochTag(von);
}

/** Wochentag: 1 = Montag … 7 = Sonntag. */
export function wochentag(d: Datum): number {
  const w = new Date(epochTag(d) * MS_PRO_TAG).getUTCDay(); // 0 = Sonntag
  return w === 0 ? 7 : w;
}

/** Heutiges Datum (Ortszeit des Geräts). */
export function heute(): Datum {
  const n = new Date();
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
}

/** Jetzige Uhrzeit (Ortszeit des Geräts) in Minuten. */
export function jetztMin(): Min {
  const n = new Date();
  return n.getHours() * 60 + n.getMinutes();
}

/** Jetzige Uhrzeit auf die Sekunde, in Millisekunden seit Mitternacht (für Countdowns). */
export function jetztMs(): number {
  const n = new Date();
  return ((n.getHours() * 60 + n.getMinutes()) * 60 + n.getSeconds()) * 1000 + n.getMilliseconds();
}

/** "12 Min", "2 Std", "1 Std 20 Min". */
export function dauerText(minuten: number): string {
  if (minuten < 60) return `${minuten} Min`;
  const std = Math.floor(minuten / 60);
  const min = minuten % 60;
  return min === 0 ? `${std} Std` : `${std} Std ${min} Min`;
}

/**
 * Countdown zu einer Stunde. [bisBeginnMs]: Millisekunden bis zum Beginn (negativ = schon begonnen);
 * [bisEndeMs]: bis zum Ende (null = unbekannt). Aufgerundet auf volle Minuten.
 * "läuft noch 12 Min" · "in 8 Min" · "jetzt" · null (vorbei bzw. mehr als 10 Stunden).
 */
export function countdownText(bisBeginnMs: number, bisEndeMs: number | null, laeuftPraefix = 'läuft noch'): string | null {
  if (bisEndeMs !== null && bisBeginnMs <= 0 && bisEndeMs > 0) {
    return `${laeuftPraefix} ${dauerText(Math.floor((bisEndeMs + 59_999) / 60_000))}`;
  }
  if (bisBeginnMs < 0) return null;
  const bis = Math.floor((bisBeginnMs + 59_999) / 60_000);
  if (bis === 0) return 'jetzt';
  if (bis > 600) return null;
  return `in ${dauerText(bis)}`;
}

const WOCHENTAGE = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];
const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const MONATE_KURZ = ['Jan.', 'Feb.', 'März', 'Apr.', 'Mai', 'Juni', 'Juli', 'Aug.', 'Sept.', 'Okt.', 'Nov.', 'Dez.'];

export function wochentagName(d: Datum): string {
  return WOCHENTAGE[wochentag(d) - 1];
}

export function monatName(d: Datum): string {
  return MONATE[Number(d.slice(5, 7)) - 1];
}

/** "Dienstag, 13. Oktober" */
export function langesDatum(d: Datum): string {
  return `${wochentagName(d)}, ${Number(d.slice(8, 10))}. ${monatName(d)}`;
}

/** "13. Okt." */
export function kurzesDatum(d: Datum): string {
  return `${Number(d.slice(8, 10))}. ${MONATE_KURZ[Number(d.slice(5, 7)) - 1]}`;
}

/** "13.10." */
export function ganzKurzesDatum(d: Datum): string {
  return `${Number(d.slice(8, 10))}.${Number(d.slice(5, 7))}.`;
}

/** "13.10.2026" */
export function deutschesDatum(d: Datum): string {
  return `${d.slice(8, 10)}.${d.slice(5, 7)}.${d.slice(0, 4)}`;
}

/** "vor 5 Min", "vor 2 Std 10 Min", "vor 3 Tagen" – wie alt ein gespeicherter Stand ist. */
export function alterText(seitMs: number, jetztMs: number = Date.now()): string {
  const min = Math.max(0, Math.floor((jetztMs - seitMs) / 60000));
  if (min < 1) return 'gerade eben';
  if (min < 60) return `vor ${min} Min`;
  if (min < 24 * 60) return `vor ${Math.floor(min / 60)} Std${min % 60 > 0 ? ` ${min % 60} Min` : ''}`;
  const t = Math.floor(min / (24 * 60));
  return `vor ${t} ${t === 1 ? 'Tag' : 'Tagen'}`;
}

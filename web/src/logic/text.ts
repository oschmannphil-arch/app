/**
 * Textwerkzeuge. Bewusst ohne Lookbehind in den Regex ("(?<!…)"): Das gibt es in Safari erst ab 16.4,
 * und ein einziger solcher Ausdruck würde ältere iPhones die ganze App kosten.
 */

/** Ganze Wörter (Buchstaben/Ziffern) – dient dem Erkennen von Kurskürzeln in Hinweistexten. */
export const WORT = /[\p{L}\p{N}]+/gu;

/** Sieht aus wie ein Kurskürzel ("DEU3", "MAT2"), auch wenn es in keiner Kursliste steht. */
export const KURS_MUSTER = /(?:^|[^\p{L}\p{N}])\p{L}{2,5}\d{1,2}(?![\p{L}\p{N}])/u;

/** Grenze zwischen zwei Aussagen in einem Hinweis: ";" oder ein freistehender Strich. */
export const SATZ_GRENZE = /;|\s+[–—-]\s+/;

export function istBuchstabeOderZiffer(c: string): boolean {
  return /[\p{L}\p{N}]/u.test(c);
}

export function istBuchstabe(c: string): boolean {
  return /\p{L}/u.test(c);
}

export function hatBuchstabeOderZiffer(s: string): boolean {
  return /[\p{L}\p{N}]/u.test(s);
}

export function hatBuchstabe(s: string): boolean {
  return /\p{L}/u.test(s);
}

export function nurStriche(s: string): boolean {
  return s.length > 0 && !hatBuchstabeOderZiffer(s);
}

/** Alle ganzen Wörter eines Textes. */
export function woerter(s: string): string[] {
  return s.match(WORT) ?? [];
}

export function gleichOhneGross(a: string, b: string): boolean {
  return a.toLowerCase() === b.toLowerCase();
}

/** Entfernt führende und folgende Zeichen, für die [istRand] gilt (Kotlin: trim { }). */
export function trimmen(s: string, istRand: (c: string) => boolean): string {
  let a = 0;
  let e = s.length;
  while (a < e && istRand(s[a])) a++;
  while (e > a && istRand(s[e - 1])) e--;
  return s.slice(a, e);
}

/** Wie Kotlin distinctBy: erstes Vorkommen bleibt, Reihenfolge bleibt. */
export function distinctBy<T, K>(liste: readonly T[], schluessel: (t: T) => K): T[] {
  const gesehen = new Set<K>();
  const out: T[] = [];
  for (const x of liste) {
    const k = schluessel(x);
    if (!gesehen.has(k)) {
      gesehen.add(k);
      out.push(x);
    }
  }
  return out;
}

/** Wie Kotlin groupBy, aber mit stabiler Reihenfolge der ersten Vorkommen. */
export function gruppieren<T, K>(liste: readonly T[], schluessel: (t: T) => K): Map<K, T[]> {
  const m = new Map<K, T[]>();
  for (const x of liste) {
    const k = schluessel(x);
    const g = m.get(k);
    if (g) g.push(x);
    else m.set(k, [x]);
  }
  return m;
}

/** Vergleich zweier Zahlen für sort(). */
export function vergleiche(a: number, b: number): number {
  return a - b;
}

/** Mehrstufiger Vergleich: gibt für die erste ungleiche Stelle das Ergebnis zurück. */
export function vergleichKette(...ergebnisse: number[]): number {
  for (const e of ergebnisse) if (e !== 0) return e;
  return 0;
}

export function textVergleich(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** "12/5" → "12", "12a" → "12", "Q1/2" → "Q1". */
export function jahrgang(klasse: string): string {
  const k = klasse.trim();
  const ziffern = /^\d*/.exec(k)![0];
  return ziffern !== '' ? ziffern : k.split('/')[0].trim();
}

import type { Lesson, TagesPlan } from './models';
import { freistunden } from './models';
import { type Zeitfenster, stundenListe } from './raster';
import { type Min, uhrzeitText } from './zeit';

/** Ein Freund mit seinen Kursen (IDs wie bei der eigenen Kurswahl, z.B. "12/5::DEU1"). */
export interface Freund {
  id: string;
  name: string;
  kurse: string[];
}

/**
 * Freie Zeit im Tagesraster, z.B. 3.–4. Stunde. [von]/[bis] sind die Stundennummern (null, wenn der
 * Plan kein Zeitraster hergibt). [beginn]/[ende] schließen angrenzende Pausen ein.
 */
export interface Freiblock {
  von: number | null;
  bis: number | null;
  beginn: Min;
  ende: Min;
}

/** "3. Std" bzw. "3.–4. Std"; null ohne Stundennummern. */
export function freiblockStundenText(b: Freiblock): string | null {
  if (b.von === null || b.bis === null) return null;
  const nr: number[] = [];
  for (let i = b.von; i <= b.bis; i++) nr.push(i);
  return stundenListe(nr);
}

export type Anwesend = 'KEIN_UNTERRICHT' | 'NOCH_NICHT' | 'DA' | 'SCHON_WEG';

/** Ob jemand zur Uhrzeit in der Schule ist – mit Beginn der ersten und Ende der letzten Stunde. */
export interface Anwesenheit {
  art: Anwesend;
  beginn: Min | null;
  ende: Min | null;
}

/** "in der Schule bis 14:25" · "kommt um 09:05" · "schon fertig (seit 12:30)" · "kein Unterricht". */
export function anwesenheitText(a: Anwesenheit): string {
  switch (a.art) {
    case 'KEIN_UNTERRICHT':
      return 'kein Unterricht';
    case 'NOCH_NICHT':
      return `kommt um ${a.beginn === null ? '' : uhrzeitText(a.beginn)}`;
    case 'DA':
      return `in der Schule bis ${a.ende === null ? '' : uhrzeitText(a.ende)}`;
    case 'SCHON_WEG':
      return `schon fertig (seit ${a.ende === null ? '' : uhrzeitText(a.ende)})`;
  }
}

export { jahrgang } from './text';
import { jahrgang } from './text';

const nurMitZeiten = (raster: readonly Zeitfenster[]) =>
  raster
    .filter((f) => f.beginn !== null && f.ende !== null && f.beginn < f.ende)
    .sort((a, b) => a.beginn! - b.beginn!);

/** Liegt die Stunde im Fenster? Über die Uhrzeit, sonst über die Stundennummer. */
function belegtIm(l: Lesson, f: Zeitfenster): boolean {
  if (l.beginn !== null && l.ende !== null) return l.beginn < f.ende! && f.beginn! < l.ende;
  return l.stunde === f.stunde;
}

/** Ist [plan] zur Uhrzeit [jetzt] noch nicht, gerade oder nicht mehr in der Schule? Ausfall zählt nicht. */
export function anwesenheit(plan: TagesPlan, jetzt: Min): Anwesenheit {
  const aktiv = plan.stunden.filter((l) => !l.entfaellt);
  const beginne = aktiv.map((l) => l.beginn).filter((x): x is Min => x !== null);
  const enden = aktiv.map((l) => l.ende).filter((x): x is Min => x !== null);
  if (beginne.length === 0 || enden.length === 0) return { art: 'KEIN_UNTERRICHT', beginn: null, ende: null };
  const beginn = Math.min(...beginne);
  const ende = Math.max(...enden);
  const art: Anwesend = jetzt < beginn ? 'NOCH_NICHT' : jetzt >= ende ? 'SCHON_WEG' : 'DA';
  return { art, beginn, ende };
}

function zusammenlegen(zeiten: Array<[Min, Min]>): Array<[Min, Min]> {
  const out: Array<[Min, Min]> = [];
  for (const [beginn, ende] of [...zeiten].sort((a, b) => a[0] - b[0])) {
    const letzte = out[out.length - 1];
    if (letzte && beginn <= letzte[1]) {
      out[out.length - 1] = [letzte[0], Math.max(letzte[1], ende)];
    } else {
      out.push([beginn, ende]);
    }
  }
  return out;
}

/** Belegte Zeiten eines Tagesplans, zusammengelegt. Ausgefallene Stunden sind frei. */
export function belegt(plan: TagesPlan): Array<[Min, Min]> {
  const roh: Array<[Min, Min]> = [];
  for (const l of plan.stunden) {
    if (l.entfaellt) continue;
    if (l.beginn !== null && l.ende !== null && l.beginn < l.ende) roh.push([l.beginn, l.ende]);
  }
  return zusammenlegen(roh);
}

/**
 * Fasst aufeinanderfolgende freie Fenster zu Blöcken zusammen. Die Pausen davor und danach gehören
 * mit zur freien Zeit. Wo vorhanden, zählen die echten Zeiten der [stunden]: frei ab Ende der
 * Stunde davor bis Beginn der Stunde danach – das Raster ist der Mehrheitswert der Schule.
 */
function bloecke(fenster: Zeitfenster[], stunden: readonly Lesson[], frei: (f: Zeitfenster) => boolean): Freiblock[] {
  const out: Freiblock[] = [];
  let i = 0;
  while (i < fenster.length) {
    if (!frei(fenster[i])) {
      i++;
      continue;
    }
    let j = i;
    while (j + 1 < fenster.length && frei(fenster[j + 1])) j++;
    const erstesFrei = fenster[i].beginn!;
    const letztesFrei = fenster[j].ende!;
    const rasterBeginn = Math.min(i > 0 ? fenster[i - 1].ende! : erstesFrei, erstesFrei);
    const rasterEnde = Math.max(j + 1 < fenster.length ? fenster[j + 1].beginn! : letztesFrei, letztesFrei);
    const davor = stunden.map((s) => s.ende).filter((e): e is Min => e !== null && e <= erstesFrei);
    const danach = stunden.map((s) => s.beginn).filter((b): b is Min => b !== null && b >= letztesFrei);
    out.push({
      von: fenster[i].stunde,
      bis: fenster[j].stunde,
      beginn: davor.length > 0 ? Math.max(...davor) : rasterBeginn,
      ende: danach.length > 0 ? Math.min(...danach) : rasterEnde
    });
    i = j + 1;
  }
  return out;
}

/**
 * Freistunden eines Plans im Zeitraster der Schule – auch die am Anfang des Tages, nicht aber die
 * nach der letzten Stunde. [ausfallIstFrei]: zählen ausgefallene Stunden als frei? [abErsterStunde]:
 * nur Lücken nach Beginn der ersten Stunde.
 */
export function freiBloecke(
  plan: TagesPlan,
  raster: readonly Zeitfenster[],
  ausfallIstFrei = false,
  abErsterStunde = false
): Freiblock[] {
  const belegend = plan.stunden.filter((l) => !ausfallIstFrei || !l.entfaellt);
  const enden = belegend.map((l) => l.ende).filter((e): e is Min => e !== null);
  if (enden.length === 0) return [];
  const letzte = Math.max(...enden);
  const fenster = nurMitZeiten(raster);
  let alle: Freiblock[];
  if (fenster.length === 0) {
    // Ohne Raster: nur Lücken zwischen zwei Stunden – mit denselben belegenden Stunden.
    alle = freistunden({ ...plan, stunden: belegend }).map(([, von, bis]) => ({ von: null, bis: null, beginn: von, ende: bis }));
  } else {
    alle = bloecke(fenster, belegend, (f) => !(f.ende! > letzte) && !belegend.some((l) => belegtIm(l, f)));
  }
  if (!abErsterStunde) return alle;
  const beginne = belegend.map((l) => l.beginn).filter((b): b is Min => b !== null);
  if (beginne.length === 0) return alle;
  const erste = Math.min(...beginne);
  return alle.filter((b) => !(b.beginn < erste));
}

/** Freistunden für die Stundenliste, je an der Stelle (Index in plan.stunden) der Stunde, die danach kommt. */
export function freiVor(plan: TagesPlan, raster: readonly Zeitfenster[]): Map<number, Freiblock> {
  const out = new Map<number, Freiblock>();
  for (const block of freiBloecke(plan, raster)) {
    const danach = plan.stunden.findIndex((l) => l.beginn !== null && !(l.beginn < block.beginn));
    if (danach >= 0) out.set(danach, block);
  }
  return out;
}

function gemeinsamFreiOhneRaster(plaene: readonly TagesPlan[], minMinuten: number): Array<[Min, Min]> {
  const zeiten = plaene.map((p) => belegt(p));
  if (zeiten.some((z) => z.length === 0)) return [];
  const von = Math.max(...zeiten.map((z) => z[0][0]));
  const bis = Math.min(...zeiten.map((z) => z[z.length - 1][1]));
  if (!(von < bis)) return [];

  const frei: Array<[Min, Min]> = [];
  let t = von;
  for (const [beginn, ende] of zusammenlegen(zeiten.flat())) {
    if (!(beginn < bis)) break;
    if (beginn > t) frei.push([t, beginn]);
    if (ende > t) t = ende;
  }
  if (t < bis) frei.push([t, bis]);
  return frei.filter(([a, b]) => b - a >= minMinuten);
}

/**
 * Zeiten, in denen ALLE frei haben (Ausfall zählt als frei) – solange alle danach noch Unterricht
 * haben. Wer an dem Tag gar keinen Unterricht hat, ist nicht in der Schule – dann gibt es keine
 * gemeinsame Zeit. Ohne Zeitraster: nur Lücken ab [minMinuten], während alle da sind.
 */
export function gemeinsamFreiAlle(plaene: readonly TagesPlan[], raster: readonly Zeitfenster[] = [], minMinuten = 30): Freiblock[] {
  if (plaene.length === 0) return [];
  const fenster = nurMitZeiten(raster);
  if (fenster.length === 0) {
    return gemeinsamFreiOhneRaster(plaene, minMinuten).map(([von, bis]) => ({ von: null, bis: null, beginn: von, ende: bis }));
  }
  const aktive = plaene.map((p) => p.stunden.filter((l) => !l.entfaellt));
  const letzteEnden: Min[] = [];
  for (const s of aktive) {
    const enden = s.map((l) => l.ende).filter((e): e is Min => e !== null);
    if (enden.length === 0) return [];
    letzteEnden.push(Math.max(...enden));
  }
  const bis = Math.min(...letzteEnden);
  return bloecke(fenster, aktive.flat(), (f) => !(f.ende! > bis) && aktive.every((s) => !s.some((l) => belegtIm(l, f))));
}

/** Wie [gemeinsamFreiAlle] für zwei Leute. */
export function gemeinsamFrei(a: TagesPlan, b: TagesPlan, raster: readonly Zeitfenster[] = [], minMinuten = 30): Freiblock[] {
  return gemeinsamFreiAlle([a, b], raster, minMinuten);
}

/**
 * Der Freiblock, in dem man zur Uhrzeit [jetzt] gerade frei ist (Ausfall zählt als frei), oder null.
 * Vor der ersten stattfindenden Stunde ist man "noch nicht da", nicht frei.
 */
export function jetztFrei(plan: TagesPlan, raster: readonly Zeitfenster[], jetzt: Min): Freiblock | null {
  return freiBloecke(plan, raster, true, true).find((b) => jetzt >= b.beginn && jetzt < b.ende) ?? null;
}

/** Ist man um [jetzt] in der Schule – zwischen Beginn der ersten und Ende der letzten Stunde? */
export function inDerSchule(plan: TagesPlan, jetzt: Min): boolean {
  return anwesenheit(plan, jetzt).art === 'DA';
}

/**
 * Schlüssel für "gleicher Unterricht": Stunde, Zeit, Kurs, Lehrkraft und Jahrgang. Die Klasse
 * innerhalb des Jahrgangs (12/5 vs. 12/6) und die Unterrichtsnummer zählen nicht – ein Kurs, der in
 * mehreren Klassen steht, ist derselbe. Ohne Kurskürzel (Klassenunterricht) gehört die ganze Klasse dazu.
 */
export function zusammenKey(l: Lesson): string {
  const kurs = l.kursKuerzel?.trim();
  if (kurs) {
    return [l.stunde, l.beginn, kurs.toLowerCase(), l.lehrer.trim().toLowerCase(), jahrgang(l.klasse)].join('|');
  }
  return [l.stunde, l.beginn, l.klasse, l.fach.trim().toLowerCase()].join('|');
}

/** Stunden aus [a], die [b] ebenfalls hat – ohne ausgefallene. */
export function gemeinsameStunden(a: TagesPlan, b: TagesPlan): Lesson[] {
  const schluesselB = new Set(b.stunden.filter((l) => !l.entfaellt).map(zusammenKey));
  return a.stunden.filter((l) => !l.entfaellt && schluesselB.has(zusammenKey(l)));
}

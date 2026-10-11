import { useEffect, useState } from 'preact/hooks';
import { kodieren as ausfallKodieren } from '../logic/ausfall';
import { planKlausurenAusStunden, type PlanKlausur } from '../logic/aufgaben';
import { type GesamtPlan, type KursInfo, type TagesPlan, type Zugang, alleKurse, tagesplanFuer } from '../logic/models';
import { type Datum, heute, plusTage } from '../logic/zeit';
import { type PlanResult } from './api';
import { cacheAufraeumen, fotosAufraeumen } from './kv';
import {
  aufgabenAblage,
  ausfallAufraeumen,
  ausfallMerken,
  kursAblage,
  planKlausurenSynchronisieren,
  zugangAblage
} from './daten';
import {
  type PersoenlicherPlan,
  ersterTagMitPlan,
  holeNaechstenVerfuegbarenPlan,
  holePersoenlichenPlan,
  holeTage,
  holeWoche
} from './repo';
import { montagVon } from '../logic/raster';
import { ablage } from './speicher';

/** Was die Oberfläche gerade zeigt – wie UiZustand in der Android-App. */
export type UiZustand =
  | { art: 'LOGIN' }
  | { art: 'LAEDT' }
  /** Zugang steht – jetzt nur noch die eigenen Kurse ankreuzen. */
  | { art: 'KURSE'; kurse: KursInfo[] }
  | { art: 'ANGEZEIGT'; plan: PersoenlicherPlan }
  | { art: 'FEHLER'; nachricht: string; zugangsproblem: boolean }
  /** Für die nächsten Tage gibt es keinen Plan: Ferien oder freie Tage. */
  | { art: 'KEIN_PLAN'; naechster: Datum | null; gesucht: boolean }
  /** Pläne sind da, aber ohne eine Stunde der gewählten Kurse. */
  | { art: 'KEINE_STUNDEN' };

/** Ein anderer Tag als der normale, den man mit Wischen oder den Pfeilen gewählt hat. */
export type TagAnsicht =
  | { art: 'LAEDT'; datum: Datum }
  | { art: 'FERTIG'; datum: Datum; plan: PersoenlicherPlan }
  | { art: 'LEER'; datum: Datum; grund: string };

export interface WochenTag {
  datum: Datum;
  plan: TagesPlan | null;
  fehler: string | null;
}

export type WochenZustand = { art: 'NICHT_GELADEN' } | { art: 'LAEDT' } | { art: 'GELADEN'; tage: WochenTag[] };

interface Zustand {
  ui: UiZustand;
  aktualisiert: boolean;
  tag: TagAnsicht | null;
  woche: WochenZustand;
  /** Die Kursliste der Schule, für "Kurse ändern" und die Kurs-Prüfung. */
  kursliste: KursInfo[];
}

const start: Zustand = { ui: { art: 'LAEDT' }, aktualisiert: false, tag: null, woche: { art: 'NICHT_GELADEN' }, kursliste: [] };
const store = ablageImSpeicher(start);

/** Ein Wert nur im Arbeitsspeicher, mit Abo-Funktion (nicht gespeichert). */
function ablageImSpeicher<T>(anfang: T) {
  let wert = anfang;
  const hoerer = new Set<() => void>();
  return {
    get: () => wert,
    set(neu: Partial<T>) {
      wert = { ...wert, ...neu };
      hoerer.forEach((f) => f());
    },
    abonnieren(f: () => void) {
      hoerer.add(f);
      return () => hoerer.delete(f);
    }
  };
}

export function useZustand(): Zustand {
  const [, z] = useState(0);
  useEffect(() => store.abonnieren(() => z((n) => n + 1)), []);
  return store.get();
}

export const zustand = () => store.get();

let ladeNr = 0;

const fehlerAuth = (): UiZustand => ({
  art: 'FEHLER',
  nachricht: 'Benutzername oder Passwort falsch. Bitte in den Einstellungen prüfen.',
  zugangsproblem: true
});
const fehlerNetz = (n: string): UiZustand => ({ art: 'FEHLER', nachricht: `Keine Verbindung: ${n}`, zugangsproblem: false });

const ferienStand = ablage<{ stand: string; naechster: Datum | null } | null>('ferien', null);

/**
 * Ferien oder freie Tage: zeigt das sofort und sucht dann, wann es weitergeht. Die Suche (bis zu 4 Wochen
 * voraus) läuft höchstens einmal am Tag; das Ergebnis wird gemerkt.
 */
async function keinPlanZeigen(z: Zugang, nr: number) {
  const stand = `${heute()}|${z.schulnummer}`;
  const gemerkt = ferienStand.get();
  if (gemerkt && gemerkt.stand === stand) {
    store.set({ ui: { art: 'KEIN_PLAN', naechster: gemerkt.naechster, gesucht: true } });
    return;
  }
  store.set({ ui: { art: 'KEIN_PLAN', naechster: null, gesucht: false } });
  try {
    const naechster = await ersterTagMitPlan(z, plusTage(heute(), 8));
    // Schlug der Abruf fehl, ist das Ergebnis unbekannt: nichts merken, beim nächsten Mal neu suchen.
    ferienStand.set({ stand, naechster });
    if (nr === ladeNr) store.set({ ui: { art: 'KEIN_PLAN', naechster, gesucht: true } });
  } catch {
    if (nr === ladeNr) store.set({ ui: { art: 'KEIN_PLAN', naechster: null, gesucht: false } });
  }
}

/** Gleicht Klausuren und Ausfälle der nächsten Tage ab (wie der Hintergrund-Abruf der Android-App). */
async function hintergrundAbgleich(z: Zugang, kurse: ReadonlySet<string>) {
  try {
    const tage = await holeTage(z, heute(), 8);
    const klausuren = new Map<Datum, PlanKlausur[]>();
    for (const [datum, e] of tage) {
      if (e.art !== 'OK' || e.aus !== 'NETZ') continue;
      const plan = tagesplanFuer(e.plan, kurse);
      klausuren.set(datum, planKlausurenAusStunden(datum, plan.stunden));
      ausfallMerken(datum, ausfallKodieren(plan.stunden));
    }
    planKlausurenSynchronisieren(klausuren);
    ausfallAufraeumen();
    void cacheAufraeumen(21 * 24 * 3600 * 1000);
    void fotosAufraeumen(aufgabenAblage.get().map((h) => h.id));
  } catch {
    /* Hintergrundarbeit: Fehler sind egal */
  }
}

async function ladeInhalt(z: Zugang, auswahl: ReadonlySet<string>, erzwingen: boolean, nr: number) {
  const aktuell = () => nr === ladeNr;
  // Ohne Kurswahl brauchen wir nur die Kursliste.
  if (auswahl.size === 0) {
    const e: PlanResult = await holeNaechstenVerfuegbarenPlan(z, heute(), 10, erzwingen);
    if (!aktuell()) return;
    switch (e.art) {
      case 'OK': {
        const kurse = alleKurse(e.plan);
        store.set({ kursliste: kurse, ui: { art: 'KURSE', kurse } });
        return;
      }
      case 'AUTH':
        return store.set({ ui: fehlerAuth() });
      case 'KEIN_PLAN':
        return keinPlanZeigen(z, nr);
      case 'NETZ_FEHLER':
        return store.set({ ui: fehlerNetz(e.nachricht) });
    }
  }

  // Sofort etwas zeigen: der zuletzt gespeicherte Plan liegt lokal und ist in Millisekunden da.
  if (store.get().ui.art !== 'ANGEZEIGT') {
    const vorab = await holePersoenlichenPlan(z, auswahl, { nurCache: true });
    if (aktuell() && vorab.art === 'OK' && vorab.plan.naechste && store.get().ui.art !== 'ANGEZEIGT') {
      store.set({ kursliste: alleKurse(vorab.plan.gesamt), ui: { art: 'ANGEZEIGT', plan: vorab.plan } });
    }
  }

  const e = await holePersoenlichenPlan(z, auswahl, { erzwingen });
  if (!aktuell()) return;
  switch (e.art) {
    case 'OK':
      store.set({ kursliste: alleKurse(e.plan.gesamt), ui: { art: 'ANGEZEIGT', plan: e.plan } });
      void hintergrundAbgleich(z, auswahl);
      return;
    case 'AUTH':
      return store.set({ ui: fehlerAuth() });
    case 'KEIN_PLAN':
      return keinPlanZeigen(z, nr);
    case 'KEINE_STUNDEN':
      return store.set({ ui: { art: 'KEINE_STUNDEN' } });
    case 'NETZ_FEHLER':
      return store.set({ ui: fehlerNetz(e.nachricht) });
  }
}

/** Lädt den persönlichen Plan neu. Läuft schon ein Ladevorgang mit [erzwingen] = false, wird nichts doppelt gestartet. */
export async function aktualisieren(erzwingen = false): Promise<void> {
  const z = zugangAblage.get();
  if (!z) {
    store.set({ ui: { art: 'LOGIN' } });
    return;
  }
  // Während der Kurswahl nicht neu laden: Sonst gingen die schon angekreuzten Kurse verloren.
  if (store.get().ui.art === 'KURSE' && !erzwingen) return;
  if (store.get().aktualisiert && !erzwingen) return;
  const nr = ++ladeNr;
  if (store.get().ui.art !== 'ANGEZEIGT') store.set({ ui: { art: 'LAEDT' } });
  store.set({ aktualisiert: true });
  try {
    await ladeInhalt(z, new Set(kursAblage.get()), erzwingen, nr);
  } finally {
    if (nr === ladeNr) store.set({ aktualisiert: false });
  }
}

export function anmelden(z: Zugang): void {
  const alt = zugangAblage.get();
  zugangAblage.set(z);
  // Die Kurswahl gehört zur Schule: Kurs-IDs einer anderen Schule passen auf nichts.
  if (alt && alt.schulnummer !== z.schulnummer) kursAblage.set([]);
  store.set({ woche: { art: 'NICHT_GELADEN' }, tag: null, ui: { art: 'LAEDT' } });
  void aktualisieren(true);
}

export function abmelden(): void {
  zugangAblage.set(null);
  kursAblage.set([]);
  store.set({ ui: { art: 'LOGIN' }, tag: null, woche: { art: 'NICHT_GELADEN' }, kursliste: [] });
}

export function kurseSpeichern(ids: string[]): void {
  kursAblage.set(ids);
  store.set({ woche: { art: 'NICHT_GELADEN' }, tag: null });
  if (ids.length === 0) {
    store.set({ ui: { art: 'KURSE', kurse: store.get().kursliste } });
    return;
  }
  void aktualisieren(true);
}

/** Die Kursliste neu holen (für "Kurse ändern", wenn noch keine geladen ist). */
export async function kursListeLaden(): Promise<KursInfo[]> {
  const z = zugangAblage.get();
  if (!z) return [];
  const e = await holeNaechstenVerfuegbarenPlan(z);
  if (e.art === 'OK') {
    const kurse = alleKurse(e.plan);
    store.set({ kursliste: kurse });
    return kurse;
  }
  return store.get().kursliste;
}

// ---------- Anderer Tag per Wischen ----------

const TAG_RUECKBLICK = 14;
const TAG_VORAUS = 28;
let tagNr = 0;
let letzterStandard: Datum | null = null;

export function tagWaehlen(datum: Datum, standard: Datum, erzwingen = false): void {
  letzterStandard = standard;
  const nr = ++tagNr;
  if (datum === standard) {
    store.set({ tag: null });
    return;
  }
  const z = zugangAblage.get();
  if (!z) {
    store.set({ tag: { art: 'LEER', datum, grund: 'Du bist nicht angemeldet.' } });
    return;
  }
  store.set({ tag: { art: 'LAEDT', datum } });
  void (async () => {
    const kurse = new Set(kursAblage.get());
    const e = (await holeTage(z, datum, 1, erzwingen))[0]?.[1];
    if (nr !== tagNr) return;
    let neu: TagAnsicht;
    if (!e) neu = { art: 'LEER', datum, grund: 'Laden fehlgeschlagen.' };
    else if (e.art === 'OK') {
      const plan = tagesplanFuer(e.plan, kurse);
      neu =
        plan.stunden.length === 0
          ? { art: 'LEER', datum, grund: 'An diesem Tag hast du laut Plan keinen Unterricht.' }
          : {
              art: 'FERTIG',
              datum,
              plan: { datum, plan, naechste: null, istHeute: false, gesamt: e.plan, geprueftUm: e.geprueftUm, ausCache: e.aus === 'CACHE' }
            };
    } else if (e.art === 'KEIN_PLAN') neu = { art: 'LEER', datum, grund: 'Für diesen Tag ist kein Plan veröffentlicht (Ferien oder frei?).' };
    else if (e.art === 'AUTH') neu = { art: 'LEER', datum, grund: 'Login fehlgeschlagen.' };
    else neu = { art: 'LEER', datum, grund: 'Laden fehlgeschlagen.' };
    store.set({ tag: neu });
  })();
}

/** Vom gerade gezeigten Tag [aktuell] zum vorigen (richtung < 0) oder nächsten Schultag. */
export function tagWischen(richtung: number, aktuell: Datum, standard: Datum, naechsterSchultag: (d: Datum, r: number) => Datum): void {
  const ziel = naechsterSchultag(aktuell, richtung);
  const h = heute();
  // Nicht endlos in die Vergangenheit oder Zukunft: Pläne gibt es nur für ein paar Wochen.
  if (ziel < plusTage(h, -TAG_RUECKBLICK) || ziel > plusTage(h, TAG_VORAUS)) return;
  tagWaehlen(ziel, standard);
}

export function tagNeuLaden(): void {
  const a = store.get().tag;
  if (a && letzterStandard) tagWaehlen(a.datum, letzterStandard, true);
}

export function tagZuruecksetzen(): void {
  tagNr++;
  store.set({ tag: null });
}

// ---------- Woche ----------

let wochenNr = 0;

export async function wocheLaden(referenz: Datum, erzwingen = false): Promise<void> {
  const z = zugangAblage.get();
  const kurse = new Set(kursAblage.get());
  if (!z || kurse.size === 0) return;
  const nr = ++wochenNr;
  store.set({ woche: { art: 'LAEDT' } });
  const ergebnisse = await holeWoche(z, montagVon(referenz), erzwingen);
  if (nr !== wochenNr) return;
  const tage: WochenTag[] = ergebnisse.map(([datum, e]) => ({
    datum,
    plan: e.art === 'OK' ? tagesplanFuer(e.plan, kurse) : null,
    fehler: e.art === 'OK' ? null : e.art === 'AUTH' ? 'Login fehlgeschlagen' : e.art === 'KEIN_PLAN' ? 'Kein Plan' : 'Laden fehlgeschlagen'
  }));
  store.set({ woche: { art: 'GELADEN', tage } });
}

export const woche = {
  zuruecksetzen: () => store.set({ woche: { art: 'NICHT_GELADEN' } })
};

export type { GesamtPlan };

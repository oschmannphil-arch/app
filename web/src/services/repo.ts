import {
  type GesamtPlan,
  type Lesson,
  type NaechsteStundeErgebnis,
  type TagesPlan,
  type Zugang,
  ersteStunde,
  hatStundeVon,
  naechsteStunde,
  tagesplanFuer
} from '../logic/models';
import { montagVon } from '../logic/raster';
import { type Datum, type Min, heute as heuteTag, jetztMin, plusTage } from '../logic/zeit';
import { type PlanResult, holePlanAusCache, ladeVomServer } from './api';

/** So lange gilt ein frisch geholter Plan als aktuell genug für weitere Anfragen. */
const FRISCH_MS = 30_000;

const frisch = new Map<string, { ergebnis: PlanResult; zeit: number }>();
const laufend = new Map<string, Promise<PlanResult>>();

const schluessel = (z: Zugang, datum: Datum) => `${z.schulnummer}|${z.benutzername}|${z.passwort.length}|${datum}`;

/**
 * Lädt den Plan eines Tages. [erzwingen] überspringt das Zwischengedächtnis (z.B. beim Tippen auf
 * "Aktualisieren"). Gleichzeitige Anfragen für denselben Tag teilen sich einen Abruf.
 */
export async function holePlan(z: Zugang, datum: Datum, erzwingen = false): Promise<PlanResult> {
  const key = schluessel(z, datum);
  const angefragtUm = Date.now();
  const treffer = frisch.get(key);
  if (!erzwingen && treffer && angefragtUm - treffer.zeit <= FRISCH_MS) return treffer.ergebnis;

  const schon = laufend.get(key);
  if (schon) {
    const e = await schon;
    // Ein erzwungener Abruf nimmt, was ein anderer in der Wartezeit geholt hat – nicht doppelt laden.
    return e;
  }
  const abruf = ladeVomServer(z, datum).then((e) => {
    if (e.art === 'OK' && e.aus === 'NETZ') frisch.set(key, { ergebnis: e, zeit: Date.now() });
    return e;
  });
  laufend.set(key, abruf);
  try {
    return await abruf;
  } finally {
    laufend.delete(key);
  }
}

/** Holt [anzahl] aufeinanderfolgende Tage ab [ab] parallel. */
export function holeTage(z: Zugang, ab: Datum, anzahl: number, erzwingen = false): Promise<Array<[Datum, PlanResult]>> {
  return Promise.all(
    Array.from({ length: anzahl }, (_, i) => {
      const tag = plusTage(ab, i);
      return holePlan(z, tag, erzwingen).then((e): [Datum, PlanResult] => [tag, e]);
    })
  );
}

/** Montag bis Freitag der Woche, in der [referenz] liegt – parallel. */
export function holeWoche(z: Zugang, referenz: Datum, erzwingen = false): Promise<Array<[Datum, PlanResult]>> {
  return holeTage(z, montagVon(referenz), 5, erzwingen);
}

/** Der persönliche Plan des Schülers für den gerade relevanten Tag. */
export interface PersoenlicherPlan {
  datum: Datum;
  plan: TagesPlan;
  naechste: NaechsteStundeErgebnis | null;
  istHeute: boolean;
  gesamt: GesamtPlan;
  geprueftUm: number;
  ausCache: boolean;
}

export type PersoenlicherResult =
  | { art: 'OK'; plan: PersoenlicherPlan }
  | { art: 'AUTH' }
  /** Es gibt keinen veröffentlichten Plan (Ferien, freie Tage). */
  | { art: 'KEIN_PLAN' }
  /** Pläne sind da, aber ohne eine Stunde der gewählten Kurse – meist eine veraltete Kurswahl. */
  | { art: 'KEINE_STUNDEN' }
  | { art: 'NETZ_FEHLER'; nachricht: string };

/**
 * Sucht die nächste relevante Unterrichtsstunde: heute, wenn noch etwas kommt – sonst die erste
 * Stunde des nächsten Schultags. Heute wird zuerst alleine geholt; nur wenn heute nichts mehr kommt,
 * werden die Folgetage parallel geholt. Mit [nurCache] geht es gar nicht ins Netz.
 */
export async function holePersoenlichenPlan(
  z: Zugang,
  kurse: ReadonlySet<string>,
  opt: { ab?: Datum; jetzt?: Min; maxTage?: number; nurCache?: boolean; erzwingen?: boolean } = {}
): Promise<PersoenlicherResult> {
  const ab = opt.ab ?? heuteTag();
  const jetzt = opt.jetzt ?? jetztMin();
  const maxTage = opt.maxTage ?? 8;
  let heuteFallback: PersoenlicherPlan | null = null;
  let planGesehen = false;

  const tag = (datum: Datum): Promise<PlanResult> =>
    opt.nurCache
      ? holePlanAusCache(z, datum).then((e) => e ?? { art: 'NETZ_FEHLER' as const, nachricht: 'Nicht gespeichert' })
      : holePlan(z, datum, opt.erzwingen);

  /** Liefert das Endergebnis, sobald es feststeht – sonst null (weitersuchen). */
  const auswerten = (offset: number, e: PlanResult): PersoenlicherResult | null => {
    const datum = plusTage(ab, offset);
    switch (e.art) {
      case 'OK': {
        planGesehen = true;
        const plan = tagesplanFuer(e.plan, kurse);
        if (offset === 0) {
          const naechste = naechsteStunde(plan, jetzt);
          const persoenlich: PersoenlicherPlan = {
            datum, plan, naechste, istHeute: true, gesamt: e.plan, geprueftUm: e.geprueftUm, ausCache: e.aus === 'CACHE'
          };
          if (naechste) return { art: 'OK', plan: persoenlich };
          // Heute ist durch – als Rückfalloption merken und morgen weitersuchen.
          heuteFallback ??= persoenlich;
          return null;
        }
        const erste = ersteStunde(plan);
        if (!erste) return null;
        return {
          art: 'OK',
          plan: {
            datum, plan, naechste: { lesson: erste, istVorschau: false }, istHeute: false, gesamt: e.plan,
            geprueftUm: e.geprueftUm, ausCache: e.aus === 'CACHE'
          }
        };
      }
      case 'KEIN_PLAN':
        return null; // Wochenende/Ferien: weiter
      case 'AUTH':
        return { art: 'AUTH' };
      case 'NETZ_FEHLER':
        // Ist heute schon bekannt, ist ein Netzfehler bei einem Folgetag kein Grund, alles zu verwerfen.
        return heuteFallback ? { art: 'OK', plan: heuteFallback } : { art: 'NETZ_FEHLER', nachricht: e.nachricht };
    }
  };

  const erstes = auswerten(0, await tag(ab));
  if (erstes) return erstes;

  if (maxTage > 1) {
    const weitere = await Promise.all(Array.from({ length: maxTage - 1 }, (_, i) => tag(plusTage(ab, i + 1))));
    for (let i = 0; i < weitere.length; i++) {
      const r = auswerten(i + 1, weitere[i]);
      if (r) return r;
    }
  }
  if (heuteFallback) return { art: 'OK', plan: heuteFallback };
  return planGesehen ? { art: 'KEINE_STUNDEN' } : { art: 'KEIN_PLAN' };
}

/** Erster erreichbarer Plan – für die Kursauswahl beim Einrichten. */
export async function holeNaechstenVerfuegbarenPlan(z: Zugang, ab: Datum = heuteTag(), maxTage = 10, erzwingen = false): Promise<PlanResult> {
  for (const [, e] of await holeTage(z, ab, maxTage, erzwingen)) {
    if (e.art !== 'KEIN_PLAN') return e;
  }
  return { art: 'KEIN_PLAN' };
}

/**
 * Der erste Tag ab [ab] (höchstens [maxTage] Tage weit), für den ein Plan veröffentlicht ist – für
 * "Ferien: der nächste Plan steht für … bereit". Wirft bei Abruffehlern ("unbekannt" ≠ "keiner da").
 */
export async function ersterTagMitPlan(z: Zugang, ab: Datum, maxTage = 28): Promise<Datum | null> {
  for (let versatz = 0; versatz < maxTage; versatz += 7) {
    const tage = await holeTage(z, plusTage(ab, versatz), Math.min(7, maxTage - versatz));
    const treffer = tage.find(([, e]) => e.art === 'OK');
    if (treffer) return treffer[0];
    if (tage.some(([, e]) => e.art === 'AUTH' || e.art === 'NETZ_FEHLER')) throw new Error('Abruf fehlgeschlagen');
  }
  return null;
}

/**
 * Datum der nächsten Stunde im selben Kurs nach [nach] – für "Hausaufgabe bis zur nächsten Stunde".
 * Sucht bis zu zwei Wochen voraus (nur, soweit Pläne veröffentlicht sind); null, wenn nichts gefunden wurde.
 */
export async function naechsteStundeVon(z: Zugang, kurse: ReadonlySet<string>, lesson: Lesson, nach: Datum): Promise<Datum | null> {
  // Woche für Woche statt 14 Tage auf einmal: Meist liegt die nächste Stunde in den ersten Tagen.
  for (let versatz = 0; versatz < 14; versatz += 4) {
    const tage = await holeTage(z, plusTage(nach, 1 + versatz), Math.min(4, 14 - versatz));
    const treffer = tage.find(([, e]) => e.art === 'OK' && hatStundeVon(tagesplanFuer(e.plan, kurse), lesson));
    if (treffer) return treffer[0];
  }
  return null;
}

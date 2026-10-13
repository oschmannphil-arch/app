import {
  type Hausaufgabe,
  type PlanKlausur,
  type Pruefung,
  PLAN_PREFIX,
  planKlausurAlsPruefung,
  planKlausurId
} from '../logic/aufgaben';
import { type Freund } from '../logic/freizeit';
import type { Zugang } from '../logic/models';
import { type Note, type NotenSystem } from '../logic/noten';
import { type Treffer, trefferAusSchluessel, trefferSchluessel } from '../logic/suche';
import { type Datum, heute, plusTage, tageZwischen } from '../logic/zeit';
import { fotoLoeschen } from './kv';
import { ablage, neueId } from './speicher';

// ---------- Zugang und Kurse ----------

export const zugangAblage = ablage<Zugang | null>('zugang', null);
export const kursAblage = ablage<string[]>('kurse', []);

// ---------- Darstellung ----------

export type DesignModus = 'SYSTEM' | 'HELL' | 'DUNKEL';
export type FarbThemaName = 'MOCHA' | 'OZEAN' | 'WALD' | 'ROSE' | 'VIOLETT' | 'EIGEN';

export interface Design {
  modus: DesignModus;
  thema: FarbThemaName;
  /** Fach (Schlüssel, klein) → Farbe als "#rrggbb". */
  fachFarben: Record<string, string>;
  symbole: boolean;
  bloecke: boolean;
  /** Farbton (0–360°) des eigenen Farbthemas. */
  eigenerTon?: number;
  /** Abstand der Stundenliste. */
  dichte?: Dichte;
  /** Eigene Anzeigenamen: Fach (Schlüssel) → Name. */
  fachNamen?: Record<string, string>;
}

export type Dichte = 'KOMPAKT' | 'NORMAL' | 'GROSS';
export const DICHTE_PX: Record<Dichte, number> = { KOMPAKT: 5, NORMAL: 10, GROSS: 16 };

export const designAblage = ablage<Design>('design', { modus: 'SYSTEM', thema: 'MOCHA', fachFarben: {}, symbole: true, bloecke: false });
export const designAendern = (teil: Partial<Design>) => designAblage.set((d) => ({ ...d, ...teil }));

// ---------- Hausaufgaben und Prüfungen ----------

/** Wie lange vergangene Prüfungen noch sichtbar bleiben, bevor sie verschwinden. */
const AUFBEWAHRUNG_TAGE = 7;

export const aufgabenAblage = ablage<Hausaufgabe[]>('aufgaben', []);
export const pruefungenAblage = ablage<Pruefung[]>('pruefungen', []);
const geloeschtAblage = ablage<string[]>('plan_klausuren_geloescht', []);

const sortierteAufgaben = (liste: Hausaufgabe[]) =>
  [...liste].sort(
    (a, b) =>
      Number(a.erledigt) - Number(b.erledigt) ||
      // Aufgaben ohne Datum ans Ende, sonst nach Fälligkeit.
      (a.faellig ?? '9999-99-99').localeCompare(b.faellig ?? '9999-99-99') ||
      a.erstelltAm - b.erstelltAm
  );

export function hausaufgabeHinzufuegen(fach: string, text: string, faellig: Datum | null): string {
  const id = neueId();
  aufgabenAblage.set((l) => sortierteAufgaben([...l, { id, fach: fach.trim(), text: text.trim(), faellig, erledigt: false, erstelltAm: Date.now() }]));
  return id;
}

export function hausaufgabeBearbeiten(id: string, fach: string, text: string, faellig: Datum | null): void {
  aufgabenAblage.set((l) => sortierteAufgaben(l.map((h) => (h.id === id ? { ...h, fach: fach.trim(), text: text.trim(), faellig } : h))));
}

export const hausaufgabeUmschalten = (id: string) =>
  aufgabenAblage.set((l) => sortierteAufgaben(l.map((h) => (h.id === id ? { ...h, erledigt: !h.erledigt } : h))));

export const hausaufgabeLoeschen = (id: string) => {
  aufgabenAblage.set((l) => l.filter((h) => h.id !== id));
  void fotoLoeschen(id);
};

const nachDatum = (l: Pruefung[]) => [...l].sort((a, b) => a.datum.localeCompare(b.datum));

export function pruefungHinzufuegen(p: Omit<Pruefung, 'id' | 'endeSekunden'>): void {
  pruefungenAblage.set((l) => nachDatum([...l, { ...p, id: neueId(), fach: p.fach.trim(), titel: p.titel.trim(), notiz: p.notiz.trim(), endeSekunden: -1 }]));
}

export function pruefungBearbeiten(id: string, teil: Pick<Pruefung, 'fach' | 'titel' | 'datum' | 'art' | 'notiz'>): void {
  pruefungenAblage.set((l) =>
    nachDatum(l.map((p) => (p.id === id ? { ...p, ...teil, fach: teil.fach.trim(), titel: teil.titel.trim(), notiz: teil.notiz.trim() } : p)))
  );
}

/** Merk-Einträge zu längst vergangenen Tagen brauchen wir nicht mehr. */
const nochRelevant = (id: string, h: Datum) => {
  const datum = id.slice(PLAN_PREFIX.length).split('|')[0];
  return /^\d{4}-\d{2}-\d{2}$/.test(datum) && datum >= plusTage(h, -AUFBEWAHRUNG_TAGE);
};

export function pruefungLoeschen(id: string): void {
  if (id.startsWith(PLAN_PREFIX)) {
    // Sonst würde die nächste Plan-Abfrage die Klausur sofort wieder anlegen.
    geloeschtAblage.set((g) => [...g.filter((x) => nochRelevant(x, heute())), id]);
  }
  pruefungenAblage.set((l) => l.filter((p) => p.id !== id));
}

/**
 * Gleicht die Klausuren aus dem Plan ab. [tage] enthält jeden Tag, der frisch geladen wurde – auch
 * Tage OHNE Klausur, damit Klausuren, die aus dem Plan verschwunden sind (verschoben/abgesagt), wieder
 * entfernt werden. Selbst angelegte Termine bleiben unberührt; gelöschte Plan-Klausuren kommen nicht wieder.
 */
export function planKlausurenSynchronisieren(tage: Map<Datum, PlanKlausur[]>): void {
  if (tage.size === 0) return;
  pruefungenAblage.set((aktuell) => {
    const geloescht = new Set(geloeschtAblage.get());
    const alleFrisch = [...tage.values()].flat();
    const frischIds = new Set(alleFrisch.map(planKlausurId));
    const behalten = aktuell.filter(
      (p) => !(p.id.startsWith(PLAN_PREFIX) && [...tage.keys()].some((d) => p.id.startsWith(`${PLAN_PREFIX}${d}|`)) && !frischIds.has(p.id))
    );
    // Bestehende Plan-Klausuren bekommen das (ggf. neue) Ende, sonst bliebe eine schon geschriebene Klausur bis Mitternacht "heute".
    const frisch = new Map(alleFrisch.map((k) => [planKlausurId(k), k]));
    const aktualisiert = behalten.map((p) => {
      const f = frisch.get(p.id);
      if (!f) return p;
      const ende = f.ende === null ? -1 : f.ende * 60;
      return p.endeSekunden === ende ? p : { ...p, endeSekunden: ende };
    });
    const vorhanden = new Set(behalten.map((p) => p.id));
    const neu = [...frisch.values()].filter((k) => !geloescht.has(planKlausurId(k)) && !vorhanden.has(planKlausurId(k))).map(planKlausurAlsPruefung);
    return nachDatum([...aktualisiert, ...neu]);
  });
}

/** Wird genau einmal beim Start aufgerufen: Abgehakte Aufgaben und alte Prüfungen verschwinden. */
export function beimStartAufraeumen(): void {
  // Die frühere Ausfall-Statistik ist entfernt – ihre gespeicherten Daten werden gelöscht.
  try {
    localStorage.removeItem('nl.ausfall');
  } catch {
    /* egal */
  }
  aufgabenAblage.set((l) => l.filter((h) => !h.erledigt));
  const grenze = plusTage(heute(), -AUFBEWAHRUNG_TAGE);
  pruefungenAblage.set((l) => l.filter((p) => p.datum > grenze));
}

// ---------- Noten ----------

export const notenAblage = ablage<Note[]>('noten', []);
export const notenAnteilAblage = ablage<Record<NotenSystem, number>>('noten_anteil', { PUNKTE: 40, NOTEN: 35 });
/** "SYSTEM|fach" → Ziel. */
export const notenZieleAblage = ablage<Record<string, number>>('noten_ziele', {});

export function notenHinzufuegen(n: Omit<Note, 'id'>): void {
  notenAblage.set((l) => [...l, { ...n, id: neueId() }].sort((a, b) => b.datum.localeCompare(a.datum)));
}
export function notenBearbeiten(id: string, n: Omit<Note, 'id' | 'system'>): void {
  notenAblage.set((l) => l.map((x) => (x.id === id ? { ...x, ...n } : x)).sort((a, b) => b.datum.localeCompare(a.datum)));
}
export const notenLoeschen = (id: string) => notenAblage.set((l) => l.filter((n) => n.id !== id));

// ---------- Freunde, Favoriten, Verlauf ----------

export const freundeAblage = ablage<Freund[]>('freunde', []);

export const freundSpeichern = (f: Freund) =>
  freundeAblage.set((l) => (l.some((x) => x.id === f.id) ? l.map((x) => (x.id === f.id ? f : x)) : [...l, f]));
export const freundLoeschen = (id: string) => freundeAblage.set((l) => l.filter((f) => f.id !== id));

/** Gemerkte Lehrkräfte und Räume der Suche ("L:Weis", "R:204"). */
export const favoritenAblage = ablage<string[]>('favoriten', []);

/** Immer gleiche Reihenfolge: erst Lehrkräfte, dann Räume, je nach Name. */
export function favoritenSortiert(schluessel: readonly string[]): Treffer[] {
  return schluessel
    .map(trefferAusSchluessel)
    .filter((t): t is Treffer => t !== null)
    .sort((a, b) => (a.art === b.art ? a.name.toLowerCase().localeCompare(b.name.toLowerCase()) : a.art === 'R' ? 1 : -1));
}

export function favoritUmschalten(t: Treffer): void {
  const k = trefferSchluessel(t);
  favoritenAblage.set((l) => (l.includes(k) ? l.filter((x) => x !== k) : [...l, k]));
}

/** Die zuletzt gewählten Lehrkräfte und Räume der Suche (neueste zuerst). */
export const verlaufAblage = ablage<string[]>('suchverlauf', []);
export const VERLAUF_MAX = 8;

export const verlaufMerken = (t: Treffer) =>
  verlaufAblage.set((l) => [trefferSchluessel(t), ...l.filter((x) => x !== trefferSchluessel(t))].slice(0, VERLAUF_MAX));
export const verlaufEntfernen = (t: Treffer) => verlaufAblage.set((l) => l.filter((x) => x !== trefferSchluessel(t)));
export const verlaufLeeren = () => verlaufAblage.set([]);

void tageZwischen;

// ---------- Änderungen seit gestern ----------

import { type Aenderung, type VerlaufsStand, aenderungenVon, fortschreiben } from '../logic/aenderungen';
import type { Lesson } from '../logic/models';

export const aenderungsVerlaufAblage = ablage<Record<string, VerlaufsStand>>('aenderungsverlauf', {});

/** Speichert den Stand eines Tages und liefert die Änderungen, die gegenüber einem früheren Tag neu sind. */
export function aenderungenSeitGestern(datum: Datum, stunden: Lesson[]): Aenderung[] {
  const aktuell = aenderungenVon(stunden);
  const keys = aktuell.map((a) => a.schluessel);
  const alle = aenderungsVerlaufAblage.get();
  const alt = alle[datum];
  const stand = fortschreiben(alt, keys, heute());
  if (JSON.stringify(stand) !== JSON.stringify(alt)) {
    // Vergangene Tage gleich mit aufräumen, damit die Ablage nicht wächst.
    const bereinigt = Object.fromEntries(Object.entries(alle).filter(([d]) => d >= heute()));
    aenderungsVerlaufAblage.set({ ...bereinigt, [datum]: stand });
  }
  const basis = stand.basis;
  return basis === null ? [] : aktuell.filter((a) => !basis.includes(a.schluessel));
}

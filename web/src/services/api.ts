import { type GesamtPlan, type Zugang } from '../logic/models';
import { parsePlan } from '../logic/parser';
import type { Datum } from '../logic/zeit';
import { cacheLesen, cacheSchreiben } from './kv';

/**
 * Pläne von Indiware mobil (stundenplan24.de). Browser dürfen diese Adresse meist nicht direkt
 * abfragen (CORS), darum geht der Abruf über den kleinen Proxy aus "worker/proxy.js". Dessen
 * Adresse steht in "config.json" neben der Seite; ohne Eintrag wird direkt versucht.
 */

export type PlanResult =
  | { art: 'OK'; plan: GesamtPlan; geprueftUm: number; aus: 'NETZ' | 'CACHE' }
  | { art: 'AUTH' }
  | { art: 'KEIN_PLAN' }
  | { art: 'NETZ_FEHLER'; nachricht: string };

export interface Konfiguration {
  /** Adresse des Proxys (ohne Schrägstrich am Ende); leer = direkt abrufen. */
  proxyUrl: string;
}

let konfig: Konfiguration | null = null;

export async function konfigurationLaden(): Promise<Konfiguration> {
  if (konfig) return konfig;
  try {
    const r = await fetch('config.json', { cache: 'no-cache' });
    if (r.ok) {
      const j = (await r.json()) as Partial<Konfiguration>;
      konfig = { proxyUrl: (j.proxyUrl ?? '').trim().replace(/\/+$/, '') };
      return konfig;
    }
  } catch {
    /* keine Konfiguration: direkt */
  }
  konfig = { proxyUrl: '' };
  return konfig;
}

const jjjjmmtt = (d: Datum) => d.replaceAll('-', '');

/** Basic-Auth-Kopf; Umlaute im Passwort werden als UTF-8 kodiert. */
export function basicKopf(z: Zugang): string {
  const bytes = new TextEncoder().encode(`${z.benutzername}:${z.passwort}`);
  let s = '';
  bytes.forEach((b) => (s += String.fromCharCode(b)));
  return `Basic ${btoa(s)}`;
}

/**
 * Text einer Antwort: Die Zeichenkodierung steht im Kopf des Plans ("<?xml … encoding=…?>") oder im
 * Content-Type. Ohne Angabe gilt UTF-8. So bleiben Umlaute auch in älteren ISO-8859-1-Plänen heil.
 */
export function dekodieren(daten: ArrayBuffer, contentType: string | null): string {
  const kopf = new TextDecoder('latin1').decode(daten.slice(0, 200));
  const aus = (/<\?xml[^>]*encoding=["']([\w-]+)["']/i.exec(kopf)?.[1] ?? /charset=([\w-]+)/i.exec(contentType ?? '')?.[1] ?? 'utf-8').toLowerCase();
  try {
    return new TextDecoder(aus).decode(daten);
  } catch {
    return new TextDecoder('utf-8').decode(daten);
  }
}

function cacheKey(z: Zugang, datum: Datum) {
  return `plan|${z.schulnummer}|${datum}`;
}

async function ausCache(z: Zugang, datum: Datum): Promise<PlanResult | null> {
  const e = await cacheLesen(cacheKey(z, datum));
  if (!e) return null;
  const plan = parsePlan(e.xml, z.schulnummer);
  return plan ? { art: 'OK', plan, geprueftUm: e.zeit, aus: 'CACHE' } : null;
}

/** Nur aus dem lokalen Speicher – für die sofortige Anzeige beim Start. */
export async function holePlanAusCache(z: Zugang, datum: Datum): Promise<PlanResult | null> {
  return ausCache(z, datum);
}

async function cacheOderFehler(z: Zugang, datum: Datum, meldung: string): Promise<PlanResult> {
  return (await ausCache(z, datum)) ?? { art: 'NETZ_FEHLER', nachricht: meldung };
}

/** Lädt einen Plan vom Server; bei Netzfehlern gilt der gespeicherte Stand. */
export async function ladeVomServer(z: Zugang, datum: Datum): Promise<PlanResult> {
  const k = await konfigurationLaden();
  const ziel = k.proxyUrl
    ? `${k.proxyUrl}/plan?schule=${encodeURIComponent(z.schulnummer)}&datum=${jjjjmmtt(datum)}`
    : `https://www.stundenplan24.de/${encodeURIComponent(z.schulnummer)}/mobil/mobdaten/PlanKl${jjjjmmtt(datum)}.xml`;
  let antwort: Response;
  try {
    antwort = await fetch(ziel, { headers: { Authorization: basicKopf(z) }, cache: 'no-store' });
  } catch {
    return cacheOderFehler(
      z,
      datum,
      k.proxyUrl ? 'Keine Verbindung.' : 'Verbindung blockiert – der Proxy ist noch nicht eingerichtet (siehe Anleitung).'
    );
  }
  if (antwort.status === 401) return { art: 'AUTH' };
  if (antwort.status === 404) return { art: 'KEIN_PLAN' };
  if (!antwort.ok) return cacheOderFehler(z, datum, `HTTP ${antwort.status}`);
  const xml = dekodieren(await antwort.arrayBuffer(), antwort.headers.get('Content-Type'));
  if (xml.trim() === '') return cacheOderFehler(z, datum, 'Leere Antwort');
  const plan = parsePlan(xml, z.schulnummer);
  // Kein gültiger Plan (HTML-Fehlerseite, Anmeldeseite im WLAN …): wie ein Netzfehler behandeln und
  // den guten Cache NICHT überschreiben.
  if (!plan) return cacheOderFehler(z, datum, 'Ungültige Antwort');
  const jetzt = Date.now();
  await cacheSchreiben(cacheKey(z, datum), { xml, zeit: jetzt });
  return { art: 'OK', plan, geprueftUm: jetzt, aus: 'NETZ' };
}

import type { Freund } from './freizeit';

/**
 * Kurse als Link verschicken. Die Web-App nutzt "…/#freund?name=Anna&kurse=12%2F5%3A%3ADEU1,…". Links
 * der Android-App ("nextlesson://freund?…") werden ebenfalls verstanden – so tauschen beide Seiten
 * Kurse aus. Wer den Link bekommt, tippt ihn an (oder fügt ihn ein) und hat den Freund samt Kursen.
 */

const ANDROID_LINK = /nextlesson:\/\/freund\/?\?\S+/i;
const WEB_LINK = /\S*#\/?freund\?\S+/i;

/** Satzzeichen, die beim Einfügen aus einer Nachricht am Link hängen bleiben können. */
const ANHAENGSEL = ').,;:!?]}>"\'»“”';

const kodieren = (s: string) => encodeURIComponent(s);
const dekodieren = (s: string): string | null => {
  try {
    return decodeURIComponent(s.replace(/\+/g, ' '));
  } catch {
    return null;
  }
};

function query(name: string, kurse: readonly string[]): string {
  return `name=${kodieren(name.trim())}&kurse=` + [...kurse].sort().map(kodieren).join(',');
}

/** Link für die Web-App; [basis] ist ihre Adresse ohne "#…". */
export const webLink = (basis: string, name: string, kurse: readonly string[]) => `${basis.split('#')[0]}#freund?${query(name, kurse)}`;

/** Link für die Android-App. */
export const androidLink = (name: string, kurse: readonly string[]) => `nextlesson://freund?${query(name, kurse)}`;

/** Text zum Verschicken: kurze Erklärung plus Link(s). */
export function nachricht(basis: string, name: string, kurse: readonly string[], eigene = true): string {
  const wessen = eigene || name.trim() === '' ? 'Meine Kurse' : `Die Kurse von ${name.trim()}`;
  return `${wessen} für die Stundenplan-App: ${webLink(basis, name, kurse)}\n(Android-App: ${androidLink(name, kurse)})`;
}

/** Findet einen Link irgendwo in [text] (auch in einer ganzen Nachricht) und liest ihn. */
export function lesen(text: string | null | undefined): Omit<Freund, 'id'> | null {
  const t = text ?? '';
  const roh = ANDROID_LINK.exec(t)?.[0] ?? WEB_LINK.exec(t)?.[0];
  if (!roh) return null;
  let treffer = roh;
  while (treffer.length > 0 && ANHAENGSEL.includes(treffer[treffer.length - 1])) treffer = treffer.slice(0, -1);
  const q = treffer.slice(treffer.indexOf('?') + 1);
  const felder = new Map<string, string>();
  for (const teil of q.split('&')) {
    const i = teil.indexOf('=');
    if (i >= 0) felder.set(teil.slice(0, i).toLowerCase(), teil.slice(i + 1));
  }
  const kurse: string[] = [];
  for (const k of (felder.get('kurse') ?? '').split(',')) {
    if (k.trim() === '') continue;
    const d = dekodieren(k);
    if (d && d.includes('::') && !kurse.includes(d)) kurse.push(d);
  }
  if (kurse.length === 0) return null;
  return { name: (dekodieren(felder.get('name') ?? '') ?? '').trim(), kurse };
}

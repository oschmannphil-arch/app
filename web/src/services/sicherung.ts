import { PLAN_PREFIX } from '../logic/aufgaben';
import { heute } from '../logic/zeit';
import { aufgabenAblage, freundeAblage, notenAblage, pruefungenAblage } from './daten';
import { ablage } from './speicher';

const TAG_MS = 24 * 3600 * 1000;
/** Nach so vielen Tagen ohne Sicherung erinnert die App. */
export const SICHERUNG_ALTER_TAGE = 21;
/** So lange blendet "Später" die Erinnerung aus. */
const SPAETER_TAGE = 7;

/** [letzte] = Zeitpunkt der letzten Sicherung (0 = noch nie), [spaeter] = Erinnerung bis dahin ausgeblendet. */
export const sicherungAblage = ablage<{ letzte: number; spaeter: number }>('sicherung', { letzte: 0, spaeter: 0 });

/** Alle Daten dieser App als Datei herunterladen (ohne Passwort) und den Zeitpunkt merken. */
export function sicherungSpeichern(): void {
  const daten: Record<string, string> = {};
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)!;
      // Das Passwort bleibt bewusst draußen: Die Sicherung ist eine Datei, die man leicht weitergibt.
      if (k.startsWith('nl.') && k !== 'nl.zugang' && k !== 'nl.sicherung') daten[k] = localStorage.getItem(k)!;
    }
  } catch {
    /* Speicher gesperrt: leere Sicherung wäre irreführend, aber die Datei zeigt es offen */
  }
  const blob = new Blob([JSON.stringify({ app: 'nextlesson-web', version: 1, daten }, null, 1)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `nextlesson-sicherung-${heute()}.json`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  sicherungAblage.set({ letzte: Date.now(), spaeter: 0 });
}

/** Gibt es überhaupt etwas, das sich zu sichern lohnt? */
export function hatEigeneDaten(): boolean {
  return aufgabenAblage.get().length > 0 || notenAblage.get().length > 0 || freundeAblage.get().length > 0 || pruefungenAblage.get().some((p) => !p.id.startsWith(PLAN_PREFIX));
}

/** Soll die Heute-Ansicht an die Sicherung erinnern? */
export function sicherungFaellig(jetzt: number, s: { letzte: number; spaeter: number }, hatDaten: boolean): boolean {
  if (!hatDaten || jetzt < s.spaeter) return false;
  return s.letzte === 0 || jetzt - s.letzte > SICHERUNG_ALTER_TAGE * TAG_MS;
}

export const sicherungSpaeter = () => sicherungAblage.set((s) => ({ ...s, spaeter: Date.now() + SPAETER_TAGE * TAG_MS }));

/** "vor 3 Tagen" / "heute" / "noch nie". */
export function sicherungAlterText(letzte: number, jetzt: number): string {
  if (letzte === 0) return 'noch nie';
  const t = Math.floor((jetzt - letzte) / TAG_MS);
  return t <= 0 ? 'heute' : t === 1 ? 'gestern' : `vor ${t} Tagen`;
}

// ---------- Dauerhafter Speicher ----------

export type SpeicherStatus = 'DAUERHAFT' | 'NORMAL' | 'UNBEKANNT';

export async function speicherStatus(): Promise<SpeicherStatus> {
  try {
    if (!navigator.storage?.persisted) return 'UNBEKANNT';
    return (await navigator.storage.persisted()) ? 'DAUERHAFT' : 'NORMAL';
  } catch {
    return 'UNBEKANNT';
  }
}

/** Bittet den Browser, die Daten nicht von selbst zu löschen. Ob er zusagt, entscheidet er selbst. */
export async function speicherAnfragen(): Promise<SpeicherStatus> {
  try {
    if (navigator.storage?.persist && !(await navigator.storage.persisted())) await navigator.storage.persist();
  } catch {
    /* kein Problem: dann bleibt es beim normalen Speicher */
  }
  return speicherStatus();
}

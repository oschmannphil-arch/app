import type { ComponentChildren, JSX } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import { mitSymbol } from '../logic/symbole';
import { type Datum, heute as heuteTag, jetztMin } from '../logic/zeit';
import { designAblage } from '../services/daten';
import { useAblage } from '../services/speicher';
import { fachFarbe } from '../theme';

// ---------- Symbole ----------

const PFADE: Record<string, string> = {
  check: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  woche: 'M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z',
  suche: 'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  stift: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  liste: 'M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z',
  einstellungen: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  teilen: 'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z',
  zurueck: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  neu: 'M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  zu: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  muell: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  stern: 'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  links: 'M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z',
  rechts: 'M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z'
};

export type IconName = keyof typeof PFADE;

export function Icon({ name }: { name: IconName }) {
  return (
    <svg class="ic" viewBox="0 0 24 24" aria-hidden="true">
      <path d={PFADE[name]} />
    </svg>
  );
}

export function IconKnopf(p: { icon: IconName; titel: string; onClick: () => void }) {
  return (
    <button class="icon-knopf" aria-label={p.titel} title={p.titel} onClick={p.onClick}>
      <Icon name={p.icon} />
    </button>
  );
}

// ---------- Design und Zeit ----------

/** Ist das Design gerade dunkel? Gilt auch, wenn der Nutzer es abweichend vom System gewählt hat. */
export function useDunkel(): boolean {
  const design = useAblage(designAblage);
  const [system, setSystem] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches);
  useEffect(() => {
    const m = window.matchMedia('(prefers-color-scheme: dark)');
    const f = () => setSystem(m.matches);
    m.addEventListener('change', f);
    return () => m.removeEventListener('change', f);
  }, []);
  return design.modus === 'SYSTEM' ? system : design.modus === 'DUNKEL';
}

/** Farbe und Anzeigename eines Fachs nach den Einstellungen. */
export function useFach() {
  const design = useAblage(designAblage);
  const dunkel = useDunkel();
  return {
    dunkel,
    farbe: (fach: string) => fachFarbe(fach, dunkel, design.fachFarben),
    anzeige: (fach: string) => (design.symbole ? mitSymbol(fach) : fach)
  };
}

/** Jetzige Uhrzeit in Minuten; zeichnet genau zur vollen Minute neu (und beim Zurückkehren in die App). */
export function useJetzt(): number {
  const [m, setM] = useState(jetztMin);
  useEffect(() => {
    let t: number;
    const takt = () => {
      setM(jetztMin());
      t = window.setTimeout(takt, 60_000 - (Date.now() % 60_000) + 100);
    };
    t = window.setTimeout(takt, 60_000 - (Date.now() % 60_000) + 100);
    const sichtbar = () => document.visibilityState === 'visible' && setM(jetztMin());
    document.addEventListener('visibilitychange', sichtbar);
    return () => {
      clearTimeout(t);
      document.removeEventListener('visibilitychange', sichtbar);
    };
  }, []);
  return m;
}

/** Heutiges Datum; wechselt über Mitternacht von selbst. */
export function useHeute(): Datum {
  const [d, setD] = useState(heuteTag);
  const jetzt = useJetzt();
  useEffect(() => setD(heuteTag()), [jetzt]);
  return d;
}

// ---------- Bausteine ----------

export function Karte(p: { children: ComponentChildren; art?: 'hoch' | 'warn' | 'info'; onClick?: () => void; style?: JSX.CSSProperties; class?: string }) {
  return (
    <div class={`karte ${p.art ?? ''} ${p.onClick ? 'klick' : ''} ${p.class ?? ''}`} onClick={p.onClick} style={p.style}>
      {p.children}
    </div>
  );
}

export function Badge(p: { children: ComponentChildren; farbe: string; text: string }) {
  return (
    <span class="badge" style={{ background: p.farbe, color: p.text }}>
      {p.children}
    </span>
  );
}

export function Chip(p: { children: ComponentChildren; an?: boolean; onClick?: () => void }) {
  return (
    <button class={`chip ${p.an ? 'an' : ''}`} onClick={p.onClick}>
      {p.children}
    </button>
  );
}

export function LeerHinweis({ text }: { text: string }) {
  return <Karte>{text}</Karte>;
}

export function Laden() {
  return (
    <div class="laden">
      <div class="kreis" />
    </div>
  );
}

export function Ueberschrift(p: { titel: string; unter?: string | null }) {
  return (
    <div style={{ padding: '6px 0' }}>
      <h2 class="gross">{p.titel}</h2>
      {p.unter && <div class="sehr-klein" style={{ color: 'var(--error)' }}>{p.unter}</div>}
    </div>
  );
}

export function Dialog(p: { titel?: string; children: ComponentChildren; knoepfe: ComponentChildren; onZu: () => void }) {
  return (
    <div class="dialog-hinten" onClick={(e) => e.target === e.currentTarget && p.onZu()}>
      <div class="dialog" role="dialog" aria-modal="true">
        {p.titel && <h2>{p.titel}</h2>}
        <div class="dialog-text">{p.children}</div>
        <div class="dialog-knoepfe">{p.knoepfe}</div>
      </div>
    </div>
  );
}

export function Schalter(p: { an: boolean; onAendern: (an: boolean) => void; titel: string }) {
  return <input type="checkbox" class="schalter" checked={p.an} aria-label={p.titel} onChange={(e) => p.onAendern((e.target as HTMLInputElement).checked)} />;
}

/** Eine Zahl mit einer Nachkommastelle und Komma. */
export const komma = (w: number) => w.toFixed(1).replace('.', ',');

/** Nachricht teilen: Teilen-Dialog des Geräts, sonst in die Zwischenablage. */
export async function teilen(text: string, titel: string): Promise<string> {
  try {
    if (navigator.share) {
      await navigator.share({ title: titel, text });
      return '';
    }
  } catch {
    return '';
  }
  try {
    await navigator.clipboard.writeText(text);
    return 'In die Zwischenablage kopiert.';
  } catch {
    return 'Teilen wird hier nicht unterstützt.';
  }
}

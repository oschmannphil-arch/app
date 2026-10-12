import { FachName } from './logic/fachname';
import { Farbton } from './logic/farbton';

/**
 * Farben der App. Wie in der Android-App: "Mocha" (warmes Braun, Karamell, Schieferblau), dazu vier
 * Farbthemen, bei denen die braune und die blaue Farbfamilie je einen neuen Ton bekommen.
 */

export type Palette = Record<string, string>;

const DUNKEL: Palette = {
  primary: '#E6C6A0', onPrimary: '#422C14', primaryContainer: '#D9B48F', onPrimaryContainer: '#3A2A16',
  secondary: '#A6C3CF', onSecondary: '#123039', secondaryContainer: '#46606B', onSecondaryContainer: '#E4EFF4',
  tertiary: '#C9B18C', onTertiary: '#35291A', tertiaryContainer: '#4F3F2C', onTertiaryContainer: '#EDD9BC',
  error: '#FFB4AB', onError: '#690005', errorContainer: '#8C1D18', onErrorContainer: '#FFDAD6',
  background: '#2E241C', onBackground: '#F3E7DA', surface: '#2E241C', onSurface: '#F3E7DA',
  surfaceVariant: '#3B4B53', onSurfaceVariant: '#C9D1D5',
  containerLowest: '#241B15', containerLow: '#33281F', container: '#3B2E26', containerHigh: '#473729', containerHighest: '#52402F',
  outline: '#A08D7B', outlineVariant: '#4E3F34'
};

const HELL: Palette = {
  primary: '#7A5A33', onPrimary: '#FFFFFF', primaryContainer: '#F0D3B0', onPrimaryContainer: '#2C1B06',
  secondary: '#4C6470', onSecondary: '#FFFFFF', secondaryContainer: '#CFE3EC', onSecondaryContainer: '#08222C',
  tertiary: '#6B5B3E', onTertiary: '#FFFFFF', tertiaryContainer: '#F4DFBA', onTertiaryContainer: '#241A04',
  error: '#B3261E', onError: '#FFFFFF', errorContainer: '#FFDAD6', onErrorContainer: '#410E0B',
  background: '#FFF8F3', onBackground: '#241A12', surface: '#FFF8F3', onSurface: '#241A12',
  surfaceVariant: '#E2E8EB', onSurfaceVariant: '#44505A',
  containerLowest: '#FFFFFF', containerLow: '#FDF2E8', container: '#F6EADE', containerHigh: '#F0E3D6', containerHighest: '#EADCCE',
  outline: '#83766A', outlineVariant: '#D5C6B8'
};

export const THEMEN: Record<string, { anzeige: string; warm: number | null; kalt: number | null }> = {
  MOCHA: { anzeige: 'Mocha', warm: null, kalt: null },
  OZEAN: { anzeige: 'Ozean', warm: 208, kalt: 188 },
  WALD: { anzeige: 'Wald', warm: 125, kalt: 150 },
  ROSE: { anzeige: 'Rose', warm: 340, kalt: 318 },
  VIOLETT: { anzeige: 'Violett', warm: 272, kalt: 250 },
  /** Farbton frei wählbar (Regler in den Einstellungen); die Werte hier sind Platzhalter. */
  EIGEN: { anzeige: 'Eigen', warm: 30, kalt: 8 }
};

// ---------- Farbumrechnung ----------

function hexZuHsv(hex: string): [number, number, number] {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  return [h, max === 0 ? 0 : d / max, max];
}

function hsvZuHex(h: number, s: number, v: number): string {
  const c = v * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = v - c;
  const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
  const k = (n: number) => Math.round((n + m) * 255).toString(16).padStart(2, '0');
  return `#${k(r)}${k(g)}${k(b)}`;
}

function umfaerben(hex: string, warm: number, kalt: number, hell: boolean): string {
  const [h, s, v] = hexZuHsv(hex);
  // Reines Weiß, Grau und Schwarz haben keinen Farbton und bleiben.
  if (s < 0.01) return hex;
  let sat = s;
  // Helle Flächen sind im Original fast weiß – ohne Anheben sähe man das Thema dort nicht.
  if (hell && sat < 0.25) sat = Math.min(sat * 2.5, 0.3);
  return hsvZuHex(Farbton.neu(h, warm, kalt), sat, v);
}

const FEHLER = new Set(['error', 'onError', 'errorContainer', 'onErrorContainer']);

/** Die Farbtöne für Hell/Dunkel und ein Thema. */
export function paletteFuer(dunkel: boolean, thema: string, eigenerTon = 30): Palette {
  const basis = dunkel ? DUNKEL : HELL;
  const vorlage = THEMEN[thema] ?? THEMEN.MOCHA;
  const t = thema === 'EIGEN' ? { ...vorlage, warm: eigenerTon, kalt: (eigenerTon + 338) % 360 } : vorlage;
  if (t.warm === null || t.kalt === null) return basis;
  const out: Palette = {};
  for (const [k, v] of Object.entries(basis)) out[k] = FEHLER.has(k) ? v : umfaerben(v, t.warm, t.kalt, !dunkel);
  return out;
}

const kebab = (s: string) => s.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase());

/** Setzt die Farben als CSS-Variablen ("--primary-container" usw.) am Wurzelelement. */
export function paletteAnwenden(dunkel: boolean, thema: string, eigenerTon = 30): void {
  const root = document.documentElement;
  for (const [k, v] of Object.entries(paletteFuer(dunkel, thema, eigenerTon))) root.style.setProperty(`--${kebab(k)}`, v);
  root.style.colorScheme = dunkel ? 'dark' : 'light';
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', paletteFuer(dunkel, thema, eigenerTon).container);
}

// ---------- Fachfarben ----------

const FACH_HELL = ['#8D5B2C', '#2F6B63', '#9C3B5A', '#5D4037', '#2A5D82', '#6A4A86', '#4C6B2A', '#B2601C'];
const FACH_DUNKEL = ['#E0B183', '#7FC7BC', '#EE9CB4', '#C7A99A', '#8FC0E4', '#C3A5E0', '#B5D08A', '#F0B278'];

/** Auswahl für "Farbe pro Fach" in den Einstellungen – in Hell und Dunkel gut lesbar. */
export const FACH_FARB_AUSWAHL = ['#D9822B', '#2E9E8F', '#D6527A', '#5C8DD6', '#8E63CE', '#6FA83A', '#C9A227', '#C25B4A', '#7B6A5F', '#4FA3C7'];

/** Farbe eines Fachs: die gewählte, sonst eine feste aus dem Namen. */
export function fachFarbe(fach: string, dunkel: boolean, eigene: Record<string, string>): string {
  const gewaehlt = eigene[fach.trim().toLowerCase()] ?? eigene[FachName.schluessel(fach)];
  if (gewaehlt) return gewaehlt;
  if (fach.trim() === '') return dunkel ? '#A08D7B' : '#83766A';
  const palette = dunkel ? FACH_DUNKEL : FACH_HELL;
  // Stabiler, vorzeichenfreier Hash über den Fachnamen (wie in der Android-App).
  let hash = 7;
  for (const c of FachName.schluessel(fach)) hash = (Math.imul(hash, 31) + c.charCodeAt(0)) & 0x7fffffff;
  return palette[hash % palette.length];
}

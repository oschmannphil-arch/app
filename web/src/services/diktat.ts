/** Spracheingabe des Browsers (Safari und Chrome bieten sie an, Firefox nicht). */
interface Erkennung {
  lang: string;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}

function konstruktor(): (new () => Erkennung) | null {
  const w = window as unknown as { SpeechRecognition?: new () => Erkennung; webkitSpeechRecognition?: new () => Erkennung };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

export const diktatMoeglich = (): boolean => konstruktor() !== null;

/**
 * Hört einmal zu und liefert den gesprochenen Text. [fertig] bekommt null, wenn nichts verstanden
 * wurde oder die Erkennung nicht erlaubt bzw. nicht verfügbar war.
 */
export function diktieren(fertig: (text: string | null) => void): void {
  const K = konstruktor();
  if (!K) return fertig(null);
  const e = new K();
  e.lang = 'de-DE';
  e.interimResults = false;
  e.maxAlternatives = 1;
  let ergebnis: string | null = null;
  e.onresult = (ev) => {
    ergebnis = ev.results[0]?.[0]?.transcript?.trim() || null;
  };
  e.onerror = () => {
    ergebnis = null;
  };
  e.onend = () => fertig(ergebnis);
  try {
    e.start();
  } catch {
    fertig(null);
  }
}

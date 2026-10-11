import { useEffect, useState } from 'preact/hooks';

/**
 * Kleine, dauerhafte Ablage im Browser (localStorage) mit Abo-Funktion für die Oberfläche. Jeder Wert
 * ist JSON; kaputte oder fehlende Einträge fallen auf den Startwert zurück. Der Zugriff ist in try/catch
 * gepackt: Im privaten Modus oder bei gesperrtem Speicher läuft die App einfach ohne Merken weiter.
 */
export interface Ablage<T> {
  get(): T;
  set(neu: T | ((alt: T) => T)): void;
  abonnieren(f: () => void): () => void;
}

const PREFIX = 'nl.';

function lesen<T>(key: string, start: T): T {
  try {
    const roh = localStorage.getItem(PREFIX + key);
    if (roh === null) return start;
    return JSON.parse(roh) as T;
  } catch {
    return start;
  }
}

export function ablage<T>(key: string, start: T): Ablage<T> {
  let wert = lesen(key, start);
  const hoerer = new Set<() => void>();
  return {
    get: () => wert,
    set(neu) {
      wert = typeof neu === 'function' ? (neu as (alt: T) => T)(wert) : neu;
      try {
        localStorage.setItem(PREFIX + key, JSON.stringify(wert));
      } catch {
        /* Speicher voll oder gesperrt: nur im Arbeitsspeicher weiter */
      }
      hoerer.forEach((f) => f());
    },
    abonnieren(f) {
      hoerer.add(f);
      return () => hoerer.delete(f);
    }
  };
}

/** Wert einer Ablage in einer Komponente – sie zeichnet sich bei jeder Änderung neu. */
export function useAblage<T>(a: Ablage<T>): T {
  const [, zaehler] = useState(0);
  useEffect(() => a.abonnieren(() => zaehler((n) => n + 1)), [a]);
  return a.get();
}

export function neueId(): string {
  const c = globalThis.crypto as Crypto | undefined;
  if (c && 'randomUUID' in c) return c.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

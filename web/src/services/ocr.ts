/**
 * Text aus einem Foto lesen (Tesseract im Browser). Die Bibliothek und das deutsche Sprachmodell sind
 * groß (mehrere MB) und werden deshalb erst beim ersten Gebrauch aus dem Netz geladen.
 */
import { aufbereiten } from '../logic/tabellentext';

const TESSERACT = 'https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/dist/tesseract.esm.min.js';

interface TessWort {
  text: string;
  bbox: { x0: number; y0: number; x1: number; y1: number };
}

interface Arbeiter {
  recognize(bild: Blob): Promise<{ data: { text: string; words?: TessWort[] } }>;
  terminate(): Promise<unknown>;
}

/** Liefert den erkannten Text oder null, wenn nichts erkannt wurde bzw. die Bibliothek nicht ladbar war. */
export async function textAusFoto(bild: Blob): Promise<string | null> {
  let arbeiter: Arbeiter | null = null;
  try {
    const url = TESSERACT;
    const modul = (await import(/* @vite-ignore */ url)) as { createWorker: (sprache: string) => Promise<Arbeiter> };
    arbeiter = await modul.createWorker('deu');
    const r = await arbeiter.recognize(bild);
    // Mit Positionen der Wörter: Tabellen bleiben Tabellen. Fehlen sie, gilt der rohe Text.
    const woerter = (r.data.words ?? []).map((x) => ({ text: x.text, links: x.bbox.x0, rechts: x.bbox.x1, oben: x.bbox.y0, unten: x.bbox.y1 }));
    const text = (woerter.length > 0 ? aufbereiten(woerter) : r.data.text).trim();
    return text === '' ? null : text;
  } catch {
    return null;
  } finally {
    try {
      await arbeiter?.terminate();
    } catch {
      /* egal */
    }
  }
}

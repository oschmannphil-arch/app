/**
 * Text aus einem Foto lesen (Tesseract im Browser). Die Bibliothek und das deutsche Sprachmodell sind
 * groß (mehrere MB) und werden deshalb erst beim ersten Gebrauch aus dem Netz geladen.
 */
const TESSERACT = 'https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/dist/tesseract.esm.min.js';

interface Arbeiter {
  recognize(bild: Blob): Promise<{ data: { text: string } }>;
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
    const text = r.data.text.trim();
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

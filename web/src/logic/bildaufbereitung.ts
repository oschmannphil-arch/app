/**
 * Bereitet ein Foto für die Texterkennung auf – vor allem Tafelbilder: Dunkler Grund mit heller Kreide
 * wird zu dunkler Schrift auf hellem Grund, ungleichmäßiges Licht wird ausgeglichen und der Kontrast
 * gespreizt. (Port von BildAufbereitung.kt)
 */

/** Grundton darunter gilt als dunkel (Tafel) und wird umgekehrt. */
const DUNKEL_GRENZE = 110;

/** [grau] sind Helligkeiten 0–255, [hintergrund] die grob geglättete Version davon (das örtliche Licht). */
export function verbessern(grau: ArrayLike<number>, hintergrund: ArrayLike<number>): Uint8ClampedArray {
  const n = grau.length;
  const aus = new Uint8ClampedArray(n);
  if (n === 0) return aus;
  let summe = 0;
  for (let i = 0; i < n; i++) summe += grau[i];
  const dunkel = summe / n < DUNKEL_GRENZE;

  // Licht ausgleichen: jeder Punkt im Verhältnis zu seiner Umgebung.
  const g = new Int32Array(n);
  const verteilung = new Int32Array(256);
  for (let i = 0; i < n; i++) {
    const v = dunkel ? 255 - grau[i] : grau[i];
    const b = dunkel ? 255 - hintergrund[i] : hintergrund[i];
    const x = Math.min(255, Math.max(0, Math.floor((v * 255) / Math.max(b, 1))));
    g[i] = x;
    verteilung[x]++;
  }

  // Kontrast spreizen zwischen dem 1. und 99. Prozent der Helligkeiten.
  const unten = Math.floor(n / 100);
  const oben = n - unten;
  let kumuliert = 0;
  let lo = 0;
  while (lo < 255 && kumuliert + verteilung[lo] <= unten) kumuliert += verteilung[lo++];
  let rest = n;
  let hi = 255;
  while (hi > 0 && rest - verteilung[hi] >= oben) rest -= verteilung[hi--];
  if (hi - lo < 20) {
    for (let i = 0; i < n; i++) aus[i] = g[i];
    return aus;
  }
  for (let i = 0; i < n; i++) aus[i] = Math.floor(((g[i] - lo) * 255) / (hi - lo));
  return aus;
}

/** Verkleinert auf höchstens [maxKante] Pixel, macht das Bild grau und wendet [verbessern] an; liefert ein PNG. */
export async function fuerTexterkennung(roh: Blob, maxKante = 2400): Promise<Blob | null> {
  try {
    const bmp = await createImageBitmap(roh);
    const f = Math.min(1, maxKante / Math.max(bmp.width, bmp.height));
    const b = Math.max(1, Math.round(bmp.width * f));
    const h = Math.max(1, Math.round(bmp.height * f));
    const cv = document.createElement('canvas');
    cv.width = b;
    cv.height = h;
    const c = cv.getContext('2d', { willReadFrequently: true })!;
    c.drawImage(bmp, 0, 0, b, h);
    const daten = c.getImageData(0, 0, b, h);
    const px = daten.data;
    const grau = new Uint8ClampedArray(b * h);
    for (let i = 0; i < grau.length; i++) grau[i] = (px[i * 4] * 299 + px[i * 4 + 1] * 587 + px[i * 4 + 2] * 114) / 1000;

    // Örtliches Licht: das Bild stark verkleinern und wieder vergrößern (weichzeichnen).
    const klein = document.createElement('canvas');
    klein.width = Math.max(1, Math.round(b / 16));
    klein.height = Math.max(1, Math.round(h / 16));
    const kc = klein.getContext('2d', { willReadFrequently: true })!;
    kc.imageSmoothingQuality = 'high';
    kc.drawImage(cv, 0, 0, klein.width, klein.height);
    const gross = document.createElement('canvas');
    gross.width = b;
    gross.height = h;
    const gc = gross.getContext('2d', { willReadFrequently: true })!;
    gc.imageSmoothingQuality = 'high';
    gc.drawImage(klein, 0, 0, b, h);
    const bgPx = gc.getImageData(0, 0, b, h).data;
    const hintergrund = new Uint8ClampedArray(b * h);
    for (let i = 0; i < hintergrund.length; i++) hintergrund[i] = (bgPx[i * 4] * 299 + bgPx[i * 4 + 1] * 587 + bgPx[i * 4 + 2] * 114) / 1000;

    const neu = verbessern(grau, hintergrund);
    for (let i = 0; i < neu.length; i++) {
      px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = neu[i];
      px[i * 4 + 3] = 255;
    }
    c.putImageData(daten, 0, 0);
    return await new Promise((ok) => cv.toBlob((x) => ok(x), 'image/png'));
  } catch {
    return null;
  }
}

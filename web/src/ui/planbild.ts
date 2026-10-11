import { schluss } from '../logic/models';
import { langesDatum, uhrzeitText } from '../logic/zeit';
import type { PersoenlicherPlan } from '../services/repo';

/** Zeichnet den Tagesplan als PNG (feste Farben, unabhängig vom Farbthema der App). */
export function planBild(pp: PersoenlicherPlan): Promise<Blob | null> {
  const breite = 1080;
  const rand = 56;
  const zeile = 120;
  const kopf = 170;
  const fuss = 130;
  const stunden = [...pp.plan.stunden].sort((a, b) => a.stunde - b.stunde);
  const hoehe = kopf + zeile * Math.max(stunden.length, 1) + fuss;
  const cv = document.createElement('canvas');
  cv.width = breite;
  cv.height = hoehe;
  const c = cv.getContext('2d');
  if (!c) return Promise.resolve(null);
  const schrift = (px: number, fett = false) => `${fett ? '700 ' : ''}${px}px system-ui, -apple-system, sans-serif`;

  c.fillStyle = '#2E241D';
  c.fillRect(0, 0, breite, hoehe);
  c.fillStyle = '#F2E6DA';
  c.font = schrift(56, true);
  c.fillText(langesDatum(pp.datum), rand, 100);
  c.fillStyle = '#B9A99A';
  c.font = schrift(30);
  c.fillText('NextLesson', rand, 146);

  let y = kopf;
  if (stunden.length === 0) {
    c.fillStyle = '#F2E6DA';
    c.font = schrift(44);
    c.fillText('Kein Unterricht', rand, y + 70);
  }
  for (const l of stunden) {
    c.fillStyle = l.entfaellt ? '#5A2A2A' : l.istKlausur ? '#6A3A32' : l.status === 'VERTRETUNG' || l.status === 'RAUMAENDERUNG' ? '#5A4B2A' : '#3B4D58';
    c.beginPath();
    c.roundRect ? c.roundRect(rand, y + 8, breite - 2 * rand, zeile - 16, 28) : c.rect(rand, y + 8, breite - 2 * rand, zeile - 16);
    c.fill();
    c.fillStyle = '#F2E6DA';
    c.font = schrift(40, true);
    c.fillText(l.beginn !== null ? uhrzeitText(l.beginn) : `${l.stunde}.`, rand + 28, y + 72);
    const name = l.fach.trim() || l.kursKuerzel || '—';
    c.fillStyle = l.entfaellt ? '#C9A9A9' : '#FFFFFF';
    c.font = schrift(44, true);
    c.fillText(name, rand + 230, y + 56);
    if (l.entfaellt) {
      c.fillRect(rand + 230, y + 42, c.measureText(name).width, 3);
    }
    c.fillStyle = '#D9CABB';
    c.font = schrift(30);
    const unter = l.entfaellt ? 'fällt aus' : l.istKlausur ? 'Klausur' : l.status === 'VERTRETUNG' && l.lehrer.trim() ? `Vertretung: ${l.lehrer}` : l.status === 'RAUMAENDERUNG' ? 'Raumänderung' : l.lehrer;
    c.fillText(unter, rand + 230, y + 98);
    const raum = l.entfaellt ? '' : l.raum;
    c.fillStyle = '#FFFFFF';
    c.font = schrift(44, true);
    c.fillText(raum, breite - rand - 28 - c.measureText(raum).width, y + 72);
    y += zeile;
  }
  const s = schluss(pp.plan);
  if (s !== null) {
    c.fillStyle = '#B9A99A';
    c.font = schrift(34);
    c.fillText(`Schluss ${uhrzeitText(s)}`, rand, y + 60);
  }
  return new Promise((ok) => cv.toBlob((b) => ok(b), 'image/png'));
}

/** Plan als Bild teilen (Teilen-Dialog des Geräts), sonst als Datei herunterladen. */
export async function planBildTeilen(pp: PersoenlicherPlan): Promise<string> {
  const blob = await planBild(pp);
  if (!blob) return 'Bild konnte nicht erstellt werden.';
  const datei = new File([blob], 'stundenplan.png', { type: 'image/png' });
  try {
    if (navigator.canShare?.({ files: [datei] })) {
      await navigator.share({ files: [datei], title: 'Stundenplan' });
      return '';
    }
  } catch {
    return ''; // abgebrochen
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'stundenplan.png';
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
  return 'Bild heruntergeladen.';
}

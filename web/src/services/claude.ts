import { loesePrompt } from '../logic/loeseprompt';

/** Neuer Claude-Chat im Browser; [q] füllt das Eingabefeld vor. */
const claudeLink = (frage: string) => `https://claude.ai/new?q=${encodeURIComponent(frage)}`;

/** Handy oder Tablet (dort gibt es das Teilen-Menü mit der Claude-App), sonst PC. */
function mobil(): boolean {
  const ua = navigator.userAgent;
  return /Android|iPhone|iPad|iPod/i.test(ua) || (navigator.maxTouchPoints > 1 && /Macintosh/.test(ua));
}

/** Macht aus einem Foto ein PNG – die Zwischenablage der Browser nimmt Bilder nur als PNG an. */
async function alsPng(bild: Blob): Promise<Blob> {
  const bmp = await createImageBitmap(bild);
  const cv = document.createElement('canvas');
  cv.width = bmp.width;
  cv.height = bmp.height;
  cv.getContext('2d')!.drawImage(bmp, 0, 0);
  return new Promise((ok, fehler) => cv.toBlob((b) => (b ? ok(b) : fehler(new Error('png'))), 'image/png'));
}

export const promptKopieren = async (fach: string, aufgabenText: string): Promise<string> => {
  try {
    await navigator.clipboard.writeText(loesePrompt(fach, aufgabenText));
    return 'Prompt kopiert.';
  } catch {
    return 'Kopieren hat nicht geklappt.';
  }
};

/**
 * Bringt Foto und Prompt in einen Claude-Chat – so nah an der Android-App wie im Browser möglich.
 * Wichtig: Vor den Aufrufen von Zwischenablage, Teilen und Fenster gibt es kein `await`, sonst lassen
 * Browser sie nicht mehr zu (sie gelten nur direkt nach dem Antippen).
 *
 * - Handy: Foto über das Teilen-Menü an die Claude-App, Prompt in der Zwischenablage.
 * - PC: claude.ai mit vorausgefülltem Prompt öffnen, Foto in die Zwischenablage (dort Strg+V).
 *
 * Diese App ruft selbst keine KI auf und speichert keinen Schlüssel.
 */
export async function claudeLoesen(bild: Blob, fach: string, aufgabenText: string): Promise<string> {
  const frage = loesePrompt(fach, aufgabenText);

  if (mobil()) {
    const kopiert = navigator.clipboard?.writeText(frage).then(() => true, () => false) ?? Promise.resolve(false);
    const datei = new File([bild], 'hausaufgabe.jpg', { type: bild.type || 'image/jpeg' });
    if (navigator.canShare?.({ files: [datei] })) {
      try {
        // Bewusst ohne Text: Die Claude-App nimmt sonst nur den Text und lässt das Foto fallen.
        await navigator.share({ files: [datei] });
      } catch {
        return ''; // abgebrochen
      }
      return (await kopiert) ? 'Prompt kopiert – in Claude ins Feld einfügen und senden.' : 'Foto geteilt. Den Prompt bitte mit „Prompt kopieren“ holen.';
    }
    // Kein Teilen möglich: weiter wie am PC.
  }

  let bildKopiert: Promise<boolean> = Promise.resolve(false);
  try {
    if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
      bildKopiert = navigator.clipboard.write([new ClipboardItem({ 'image/png': alsPng(bild) })]).then(() => true, () => false);
    }
  } catch {
    /* Zwischenablage für Bilder nicht verfügbar */
  }
  window.open(claudeLink(frage), '_blank');
  if (await bildKopiert) return 'Claude ist mit dem Prompt geöffnet. Dort Strg+V drücken, um das Foto einzufügen, dann senden.';
  // Ohne Bild in der Zwischenablage: Foto herunterladen, dann in Claude hochladen.
  const url = URL.createObjectURL(bild);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'hausaufgabe.jpg';
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
  return 'Claude ist mit dem Prompt geöffnet. Das Foto wurde heruntergeladen – dort über die Büroklammer hochladen, dann senden.';
}

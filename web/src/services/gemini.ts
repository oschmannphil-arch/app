import { fotoLesen } from './kv';

const GEMINI = 'https://gemini.google.com/app';

/**
 * Schickt das Foto samt Aufgabentext an die Gemini-App: über das Teilen-Menü des Geräts, wo man die
 * Gemini-App wählt. Geht das nicht (z. B. am PC), wird die Frage kopiert, das Foto heruntergeladen und
 * Gemini im Browser geöffnet. Diese App ruft selbst keine KI auf und speichert keinen Schlüssel.
 */
export async function geminiLoesen(id: string, aufgabenText: string): Promise<string> {
  const frage = `Löse diese Hausaufgabe Schritt für Schritt und erkläre den Rechenweg verständlich.${aufgabenText.trim() ? `\n\nAufgabe: ${aufgabenText.trim()}` : ''}`;
  const bild = await fotoLesen(id);
  if (!bild) return 'Kein Foto gefunden.';
  const datei = new File([bild], 'hausaufgabe.jpg', { type: bild.type || 'image/jpeg' });
  try {
    if (navigator.canShare?.({ files: [datei] })) {
      await navigator.share({ files: [datei], text: frage });
      return '';
    }
  } catch {
    return ''; // abgebrochen
  }
  // Ohne Teilen-Menü: Frage kopieren, Foto speichern, Gemini öffnen – dort Foto und Frage einfügen.
  try {
    await navigator.clipboard.writeText(frage);
  } catch {
    /* egal */
  }
  const url = URL.createObjectURL(bild);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'hausaufgabe.jpg';
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
  window.open(GEMINI, '_blank', 'noopener');
  return 'Frage kopiert, Foto heruntergeladen. In Gemini beides einfügen bzw. hochladen.';
}

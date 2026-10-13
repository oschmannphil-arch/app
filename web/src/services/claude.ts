import { loesePrompt } from '../logic/loeseprompt';
import { fotoLesen } from './kv';

const CLAUDE = 'https://claude.ai/new';

/**
 * Schickt das Foto an die Claude-App (über das Teilen-Menü des Geräts); der Prompt liegt in der
 * Zwischenablage. Geht das nicht (z. B. am PC), wird die Frage kopiert, das Foto heruntergeladen und
 * Claude im Browser geöffnet. Diese App ruft selbst keine KI auf und speichert keinen Schlüssel.
 */
export async function claudeLoesen(id: string, fach: string, aufgabenText: string): Promise<string> {
  const frage = loesePrompt(fach, aufgabenText);
  const bild = await fotoLesen(id);
  if (!bild) return 'Kein Foto gefunden.';
  const datei = new File([bild], 'hausaufgabe.jpg', { type: bild.type || 'image/jpeg' });
  // Manche KI-Apps übernehmen beim Teilen nur das Foto – darum liegt die Frage auch in der Zwischenablage.
  try {
    await navigator.clipboard.writeText(frage);
  } catch {
    /* egal */
  }
  try {
    if (navigator.canShare?.({ files: [datei] })) {
      // Bewusst ohne Text: Die Claude-App nimmt sonst nur den Text und lässt das Foto fallen.
      await navigator.share({ files: [datei] });
      return 'Prompt kopiert – in der App ins Feld einfügen.';
    }
  } catch {
    return ''; // abgebrochen
  }
  // Ohne Teilen-Menü: Foto speichern und Claude öffnen – dort Foto hochladen und die Frage einfügen.
  const url = URL.createObjectURL(bild);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'hausaufgabe.jpg';
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
  window.open(CLAUDE, '_blank', 'noopener');
  return 'Frage kopiert, Foto heruntergeladen. In Claude beides einfügen bzw. hochladen.';
}

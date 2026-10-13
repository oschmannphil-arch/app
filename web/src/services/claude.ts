import { fotoLesen } from './kv';

const CLAUDE = 'https://claude.ai/new';

/**
 * Schickt das Foto samt Aufgabentext an die Claude-App: über das Teilen-Menü des Geräts, wo man die
 * Claude-App wählt. Geht das nicht (z. B. am PC), wird die Frage kopiert, das Foto heruntergeladen und
 * Claude im Browser geöffnet. Diese App ruft selbst keine KI auf und speichert keinen Schlüssel.
 */
export async function claudeLoesen(id: string, aufgabenText: string): Promise<string> {
  const frage = `Löse diese Hausaufgabe Schritt für Schritt und erkläre den Rechenweg verständlich.${aufgabenText.trim() ? `\n\nAufgabe: ${aufgabenText.trim()}` : ''}`;
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
      await navigator.share({ files: [datei], text: frage });
      return 'Prompt kopiert – falls er in der App fehlt, im Feld einfügen.';
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

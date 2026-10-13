import { FachName } from './fachname';

/** Der Auftrag an die KI-App beim "Mit Claude lösen" – je nach Fach passend formuliert. (Port von LoesePrompt.kt) */
const ALLGEMEIN = 'Löse diese Hausaufgabe Schritt für Schritt und erkläre den Weg verständlich.';
const SCHLUSS = ' Wenn etwas auf dem Foto unleserlich ist, sag es, statt zu raten.';

const sprache = (name: string, s: string) =>
  `Bearbeite diese ${name}-Aufgabe. Schreibe die Lösung auf ${s} und erkläre darunter auf Deutsch kurz, warum sie stimmt (Grammatik, Vokabeln).`;

/** [kuerzel, text]: Das Fach passt, wenn sein Schlüssel gleich dem Kürzel ist oder (ab 3 Buchstaben) damit beginnt. */
const REGELN: Array<[string[], string]> = [
  [['mat', 'math'], 'Löse diese Mathe-Hausaufgabe Schritt für Schritt. Zeige jeden Rechenschritt, nenne die verwendete Formel oder Regel und mach am Ende eine kurze Probe.'],
  [['phy'], 'Löse diese Physik-Aufgabe in dieser Reihenfolge: gegeben, gesucht, Formel, Einsetzen mit Einheiten, Ergebnis mit Einheit.'],
  [['che'], 'Löse diese Chemie-Aufgabe Schritt für Schritt. Stelle Reaktionsgleichungen ausgeglichen auf, rechne mit Einheiten und erkläre kurz die Chemie dahinter.'],
  [['bio'], 'Beantworte diese Biologie-Aufgabe in vollständigen Sätzen mit den richtigen Fachbegriffen und erkläre die Zusammenhänge kurz.'],
  [['inf', 'info'], 'Löse diese Informatik-Aufgabe. Zeig die Lösung bzw. den Code, erkläre ihn Schritt für Schritt und gib ein kurzes Beispiel.'],
  [['deu'], 'Bearbeite diese Deutsch-Aufgabe in gutem, sachlichem Deutsch. Belege Aussagen mit Textstellen und erkläre kurz deine Überlegungen.'],
  [['eng'], sprache('Englisch', 'Englisch')],
  [['fra', 'franz'], sprache('Französisch', 'Französisch')],
  [['spa', 'span'], sprache('Spanisch', 'Spanisch')],
  [['rus'], sprache('Russisch', 'Russisch')],
  [['lat'], 'Übersetze bzw. bearbeite diese Latein-Aufgabe. Gib die Übersetzung Satz für Satz und erkläre die Grammatik (Kasus, Tempus, Konstruktionen).'],
  [['ges'], 'Beantworte diese Geschichts-Aufgabe in klaren Sätzen. Nenne Zeitraum, Ursachen und Folgen und erkläre die Zusammenhänge.'],
  [['pol', 'sozi', 'sk', 'gk', 'wir'], 'Beantworte diese Aufgabe sachlich in klaren Sätzen. Erkläre die Begriffe, stelle verschiedene Sichtweisen dar und begründe.'],
  [['geo', 'erd'], 'Beantworte diese Geografie-Aufgabe in klaren Sätzen. Nenne Ort, Ursachen und Zusammenhänge und erkläre die Fachbegriffe.'],
  [['rel', 'eth', 'phi'], 'Beantworte diese Aufgabe. Stelle die Positionen verständlich dar und begründe eine eigene Einschätzung nachvollziehbar.'],
  [['kun', 'mus'], 'Bearbeite diese Aufgabe. Beschreibe und analysiere mit den passenden Fachbegriffen und begründe deine Deutung.'],
  [['spo'], 'Beantworte diese Sport-Aufgabe in klaren Sätzen und erkläre kurz die Regeln bzw. den Hintergrund.']
];

const passt = (f: string, k: string) => f === k || (k.length >= 3 && f.startsWith(k));

/** Der Prompt für [fach] (Schreibweise egal: "MAT2", "Mathe", "mat"); [aufgabe] ist der Aufgabentext. */
export function loesePrompt(fach: string, aufgabe: string): string {
  const f = FachName.schluessel(fach);
  // "Ges…" steht für Geschichte, aber auch für Gesundheit – die bekommt den allgemeinen Prompt.
  const text = f.startsWith('gesund') ? ALLGEMEIN : (REGELN.find(([ks]) => ks.some((k) => passt(f, k)))?.[1] ?? ALLGEMEIN);
  return text + SCHLUSS + (aufgabe.trim() ? `\n\nAufgabe: ${aufgabe.trim()}` : '');
}

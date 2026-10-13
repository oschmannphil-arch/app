import { FachName } from './fachname';

/** Der Auftrag an die KI-App beim "Mit Claude lösen" – je nach Fach passend formuliert. (Port von LoesePrompt.kt) */
const ALLGEMEIN = 'Löse diese Hausaufgabe Schritt für Schritt und erkläre den Weg verständlich.';
const SCHLUSS = ' Wenn etwas auf dem Foto unleserlich ist, sag es, statt zu raten.';

const sprache = (name: string, s: string) =>
  `Bearbeite diese ${name}-Aufgabe. Schreibe die Lösung auf ${s} und erkläre darunter auf Deutsch kurz, warum sie stimmt (Grammatik, Vokabeln).`;

/**
 * [kuerzel, anfaenge, text]: Das Fach passt, wenn sein Schlüssel genau eins der Kürzel ist oder mit einem der
 * Wortanfänge beginnt – dieselben Kürzel wie bei den Fach-Symbolen, damit beides dieselben Fächer erkennt.
 */
const REGELN: Array<[string[], string[], string]> = [
  [['m', 'ma', 'mat', 'mathe'], ['mathem'], 'Löse diese Mathe-Hausaufgabe Schritt für Schritt. Zeige jeden Rechenschritt, nenne die verwendete Formel oder Regel und mach am Ende eine kurze Probe.'],
  [['ph', 'phy'], ['physik'], 'Löse diese Physik-Aufgabe in dieser Reihenfolge: gegeben, gesucht, Formel, Einsetzen mit Einheiten, Ergebnis mit Einheit.'],
  [['ch', 'che', 'chem'], ['chemie'], 'Löse diese Chemie-Aufgabe Schritt für Schritt. Stelle Reaktionsgleichungen ausgeglichen auf, rechne mit Einheiten und erkläre kurz die Chemie dahinter.'],
  [['bi', 'bio'], ['biolog'], 'Beantworte diese Biologie-Aufgabe in vollständigen Sätzen mit den richtigen Fachbegriffen und erkläre die Zusammenhänge kurz.'],
  [['if', 'inf', 'info'], ['informatik'], 'Löse diese Informatik-Aufgabe. Zeig die Lösung bzw. den Code, erkläre ihn Schritt für Schritt und gib ein kurzes Beispiel.'],
  [['d', 'de', 'deu'], ['deutsch'], 'Bearbeite diese Deutsch-Aufgabe in gutem, sachlichem Deutsch. Belege Aussagen mit Textstellen und erkläre kurz deine Überlegungen.'],
  [['e', 'en', 'eng', 'engl'], ['englisch'], sprache('Englisch', 'Englisch')],
  [['f', 'fr', 'fra', 'frz', 'franz'], ['franz'], sprache('Französisch', 'Französisch')],
  [['es', 'spa', 'span'], ['spanisch'], sprache('Spanisch', 'Spanisch')],
  [['ru', 'rus'], ['russisch'], sprache('Russisch', 'Russisch')],
  [['l', 'la', 'lat'], ['latein'], 'Übersetze bzw. bearbeite diese Latein-Aufgabe. Gib die Übersetzung Satz für Satz und erkläre die Grammatik (Kasus, Tempus, Konstruktionen).'],
  [['g', 'ge', 'gs', 'ges', 'gsch', 'gesch', 'geschi', 'gschi'], ['gesch'], 'Beantworte diese Geschichts-Aufgabe in klaren Sätzen. Nenne Zeitraum, Ursachen und Folgen und erkläre die Zusammenhänge.'],
  [['sk', 'gk', 'pol', 'soz', 'sozi', 'wi', 'wirt', 'wr'], ['sozial', 'politik', 'gemeinschaftskunde', 'wirtschaft'], 'Beantworte diese Aufgabe sachlich in klaren Sätzen. Erkläre die Begriffe, stelle verschiedene Sichtweisen dar und begründe.'],
  [['ek', 'geo', 'erd'], ['geogr', 'erdkunde'], 'Beantworte diese Geografie-Aufgabe in klaren Sätzen. Nenne Ort, Ursachen und Zusammenhänge und erkläre die Fachbegriffe.'],
  [['re', 'rel', 'reli', 'et', 'eth', 'phil'], ['religion', 'ethik', 'philosoph'], 'Beantworte diese Aufgabe. Stelle die Positionen verständlich dar und begründe eine eigene Einschätzung nachvollziehbar.'],
  [['ku', 'kun', 'kue', 'bk', 'mu', 'mus'], ['kunst', 'musik'], 'Bearbeite diese Aufgabe. Beschreibe und analysiere mit den passenden Fachbegriffen und begründe deine Deutung.'],
  [['sp', 'spo', 'spt'], ['sport'], 'Beantworte diese Sport-Aufgabe in klaren Sätzen und erkläre kurz die Regeln bzw. den Hintergrund.']
];

/** Der Prompt für [fach] (Schreibweise egal: "MAT2", "Ma", "Mathematik"); [aufgabe] ist der Aufgabentext. */
export function loesePrompt(fach: string, aufgabe: string): string {
  const f = FachName.schluessel(fach);
  const text = REGELN.find(([kuerzel, anfaenge]) => kuerzel.includes(f) || anfaenge.some((a) => f.startsWith(a)))?.[2] ?? ALLGEMEIN;
  return text + SCHLUSS + (aufgabe.trim() ? `\n\nAufgabe: ${aufgabe.trim()}` : '');
}

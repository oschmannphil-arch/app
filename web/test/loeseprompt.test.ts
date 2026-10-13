import { describe, expect, it } from 'vitest';
import { loesePrompt } from '../src/logic/loeseprompt';

describe('Lösen-Prompt je Fach', () => {
  it('Mathe', () => expect(loesePrompt('MAT2', '')).toMatch(/^Löse diese Mathe-Hausaufgabe/));
  it('Physik in jeder Schreibweise', () => {
    expect(loesePrompt('PHY1', '')).toContain('gegeben, gesucht');
    expect(loesePrompt('Physik', '')).toContain('gegeben, gesucht');
  });
  it('Sprache verlangt die Lösung in der Sprache', () => expect(loesePrompt('ENG2', '')).toContain('auf Englisch'));
  it('unbekanntes Fach bekommt den allgemeinen Prompt', () => expect(loesePrompt('XYZ', '')).toMatch(/^Löse diese Hausaufgabe Schritt für Schritt/));
  it('Aufgabentext wird angehängt', () => {
    const p = loesePrompt('DEU1', '  Aufgabe 3  ');
    expect(p.endsWith('Aufgabe: Aufgabe 3')).toBe(true);
    expect(p).toContain('unleserlich');
  });
  it('Gesundheit ist keine Geschichte', () => expect(loesePrompt('Gesundheit', '')).toMatch(/^Löse diese Hausaufgabe Schritt für Schritt/));
  it('Geschichte', () => expect(loesePrompt('GES1', '')).toMatch(/^Beantworte diese Geschichts-Aufgabe/));
  it('ohne Fach allgemein', () => expect(loesePrompt('', '')).toMatch(/^Löse diese Hausaufgabe/));
});

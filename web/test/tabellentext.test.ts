import { describe, expect, it } from 'vitest';
import { type Wort, abschnitte, aufbereiten } from '../src/logic/tabellentext';

const w = (text: string, x: number, y: number, breite = text.length * 10): Wort => ({ text, links: x, rechts: x + breite, oben: y, unten: y + 20 });

describe('Tabellen aus Foto-Text', () => {
  it('einfacher Text bleibt Zeilentext', () => {
    expect(aufbereiten([w('Aufgabe', 0, 0), w('3', 90, 0), w('lösen', 0, 40)])).toBe('Aufgabe 3\nlösen');
  });
  it('Tabelle wird mit Trennern geschrieben', () => {
    const t = aufbereiten([
      w('Tag', 0, 0), w('Fach', 200, 0), w('Seite', 400, 0),
      w('Mo', 0, 40), w('Mathe', 200, 40), w('12', 400, 40),
      w('Di', 0, 80), w('Englisch', 200, 80), w('7', 400, 80)
    ]);
    expect(t).toBe('Tag | Fach | Seite\nMo | Mathe | 12\nDi | Englisch | 7');
  });
  it('eine einzelne Zeile mit Lücke ist noch keine Tabelle', () => {
    expect(aufbereiten([w('Links', 0, 0), w('Rechts', 300, 0), w('darunter', 0, 40)])).toBe('Links Rechts\ndarunter');
  });
  it('Wörter in einer Zelle bleiben zusammen', () => {
    expect(aufbereiten([w('Fach', 0, 0), w('Thema', 300, 0), w('Deutsch', 0, 40), w('Gedicht', 300, 40), w('Analyse', 380, 40)])).toBe('Fach | Thema\nDeutsch | Gedicht Analyse');
  });
  it('Abschnitte trennen Tabelle von Text', () => {
    const a = abschnitte('Hallo\nA | B\nC | D\nEnde');
    expect(a).toEqual([
      { art: 'NORMAL', text: 'Hallo' },
      { art: 'TABELLE', zeilen: [['A', 'B'], ['C', 'D']] },
      { art: 'NORMAL', text: 'Ende' }
    ]);
  });
});

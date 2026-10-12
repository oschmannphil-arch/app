import { describe, expect, it } from 'vitest';
import { verbessern } from '../src/logic/bildaufbereitung';

describe('Bild für die Texterkennung aufbereiten', () => {
  it('Tafel wird zu dunkler Schrift auf hellem Grund', () => {
    const grau = Array.from({ length: 100 }, (_, i) => (i % 10 === 0 ? 220 : 40));
    const bg = new Array(100).fill(60);
    const neu = verbessern(grau, bg);
    expect(neu[1]).toBeGreaterThan(200);
    expect(neu[0]).toBeLessThan(80);
  });
  it('helle Seite bleibt dunkle Schrift auf hellem Grund', () => {
    const grau = Array.from({ length: 100 }, (_, i) => (i % 10 === 0 ? 30 : 230));
    const neu = verbessern(grau, new Array(100).fill(210));
    expect(neu[0]).toBeLessThan(neu[1]);
    expect(neu[1]).toBeGreaterThan(200);
  });
  it('ungleichmäßiges Licht wird ausgeglichen', () => {
    const grau = Array.from({ length: 200 }, (_, i) => {
      const links = i < 100;
      const schrift = i % 10 === 0;
      return links ? (schrift ? 100 : 200) : schrift ? 50 : 100;
    });
    const bg = Array.from({ length: 200 }, (_, i) => (i < 100 ? 190 : 95));
    const neu = verbessern(grau, bg);
    expect(Math.abs(neu[1] - neu[101])).toBeLessThan(40);
  });
});

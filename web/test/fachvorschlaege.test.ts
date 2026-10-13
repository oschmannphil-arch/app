import { describe, expect, it } from 'vitest';
import { fachVorschlaege } from '../src/logic/fachname';

const alle = ['GES', 'GEO', 'Deutsch', 'ENG', 'Englisch', 'MAT', 'Religion'];

describe('Fach-Vorschläge', () => {
  it('GE zeigt GES und GEO', () => expect(fachVorschlaege(alle, 'GE')).toEqual(['GEO', 'GES']));
  it('Groß/klein egal', () => expect(fachVorschlaege(alle, 'ge')).toEqual(['GEO', 'GES']));
  it('Anfang vor Mitte', () => expect(fachVorschlaege(alle, 'e')).toEqual(['ENG', 'Englisch', 'Deutsch', 'GEO', 'GES', 'Religion']));
  it('leer oder genauer Treffer zeigt nichts', () => {
    expect(fachVorschlaege(alle, ' ')).toEqual([]);
    expect(fachVorschlaege(alle, 'MAT')).toEqual([]);
  });
});

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

import { gleichesFach } from '../src/logic/fachname';
import { hatFach } from '../src/logic/models';
import { neueStunde } from '../src/logic/models';

describe('Nächster Block eines Fachs', () => {
  it('gleiches Fach trotz anderer Schreibweise', () => {
    expect(gleichesFach('Ma', 'MAT2')).toBe(true);
    expect(gleichesFach('Mathematik', 'MAT2')).toBe(true);
    expect(gleichesFach('GEO2', 'Geo')).toBe(true);
    expect(gleichesFach('Mathe', 'Deutsch')).toBe(false);
    expect(gleichesFach('', 'MAT2')).toBe(false);
  });
  it('hatFach findet Fach oder Kurskürzel, aber keinen Ausfall', () => {
    const plan = { kopf: { datumPlan: '', zeitstempel: '', schulnummer: '' }, klasse: 'x', stunden: [neueStunde({ stunde: 1, fach: 'ENG2', kursKuerzel: 'ENG2' }), neueStunde({ stunde: 2, fach: 'MAT2', entfaellt: true })], hinweise: [] };
    expect(hatFach(plan as never, 'Englisch')).toBe(true);
    expect(hatFach(plan as never, 'Mathe')).toBe(false);
    expect(hatFach(plan as never, 'Sport')).toBe(false);
  });
});

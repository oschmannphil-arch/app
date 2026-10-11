import { describe, expect, it } from 'vitest';
import { aenderungenVon, aenderungsText, fortschreiben } from '../src/logic/aenderungen';
import { neueStunde } from '../src/logic/models';

describe('Änderungen seit gestern', () => {
  it('erkennt Ausfall, Vertretung und Raum', () => {
    const a = aenderungenVon([
      neueStunde({ stunde: 1, entfaellt: true, fach: 'MAT' }),
      neueStunde({ stunde: 2, lehrerGeaendert: true, lehrer: 'Mei', originalTeacher: 'Got', fach: 'DEU' }),
      neueStunde({ stunde: 3, raumGeaendert: true, raum: '235', originalRoom: '033', fach: 'ENG' }),
      neueStunde({ stunde: 4, lehrerGeaendert: true, lehrer: '---', fach: 'BIO' }),
      neueStunde({ stunde: 5 })
    ]);
    expect(a.map((x) => x.art)).toEqual(['ENTFALL', 'VERTRETUNG', 'RAUM']);
    expect(aenderungsText(a).split('\n')).toEqual([
      '1. Std MAT: fällt aus',
      '2. Std DEU: Vertretung bei Mei statt Got',
      '3. Std ENG: Raum 235 statt 033'
    ]);
  });

  it('Basis entsteht erst am nächsten Kalendertag', () => {
    const s1 = fortschreiben(undefined, ['a'], '2026-10-12');
    expect(s1.basis).toBeNull();
    const s2 = fortschreiben(s1, ['a', 'b'], '2026-10-12');
    expect(s2.basis).toBeNull();
    const s3 = fortschreiben(s2, ['a', 'b', 'c'], '2026-10-13');
    expect(s3.basis).toEqual(['a', 'b']);
    expect(fortschreiben(s3, ['a'], '2026-10-13').basis).toEqual(['a', 'b']);
  });
});

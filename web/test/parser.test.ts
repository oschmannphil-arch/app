import { describe, expect, it } from 'vitest';
import { alsBloecke, blockStundenKurz, blockStundenText, blockZusammengefasst } from '../src/logic/bloecke';
import { hinweisKurz } from '../src/logic/hinweis';
import { type Lesson, type TagesPlan, freistunden, hatStundeVon, neueStunde, schluss } from '../src/logic/models';
import { stundenListe } from '../src/logic/raster';
import { parseUhrzeit } from '../src/logic/zeit';
import { plan as parse } from './helfer';

const t = (s: string) => parseUhrzeit(s)!;

describe('Parser: Lehrer und Ausfall-Wendungen', () => {
  const lesson = (lehrerEl: string, info = ''): Lesson => {
    const g = parse(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>DEU1</KKz></Ku></Kurse><Pl>
<Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>DEU1</Fa>${lehrerEl}<Ra>033</Ra><Nr></Nr><If>${info}</If></Std>
</Pl></Kl></Klassen></WplanVp>`);
    expect(g.klassen).toHaveLength(1);
    expect(g.klassen[0].stunden).toHaveLength(1);
    return g.klassen[0].stunden[0];
  };

  it('Lehrer mit ä und "Frei"/"Eva" bleiben erhalten', () => {
    // "Händel" enthält "änd", "Frei" und "Eva" sind Ausfall-Stichwörter – alles echte Namen.
    for (const name of ['Händel', 'Brändle', 'Frei', 'Eva']) {
      const l = lesson(`<Le>${name}</Le>`);
      expect(l.lehrer).toBe(name);
      expect(l.entfaellt, `${name}: kein Ausfall`).toBe(false);
    }
  });

  it('Platzhalter werden leer', () => {
    expect(lesson('<Le LeAe="x">LeGeaendert</Le>').lehrer).toBe('');
    expect(lesson('<Le>LeAe</Le>').lehrer).toBe('');
  });

  it('Stichwort im Lehrer-Feld bleibt Ausfall', () => {
    expect(lesson('<Le>Selbstständiges Arbeiten</Le>').entfaellt).toBe(true);
  });

  it('Entfall-Wendungen nur als ganze Wendung', () => {
    // Kein Ausfall: "kein" und "unterricht" bzw. "fällt" und "aus" stehen nur zufällig im Text.
    expect(lesson('<Le>Mei</Le>', 'Vertretungsunterricht, keine Pause').entfaellt).toBe(false);
    expect(lesson('<Le>Mei</Le>', 'Treffen fällt in die Pause, Hausaufgaben im Plan').entfaellt).toBe(false);
    // Echter Ausfall.
    expect(lesson('<Le>Mei</Le>', 'fällt heute aus').entfaellt).toBe(true);
    expect(lesson('<Le>Mei</Le>', 'Heute kein Unterricht').entfaellt).toBe(true);
    expect(lesson('<Le>Mei</Le>', 'entfällt').entfaellt).toBe(true);
  });

  it('ungültiges XML ergibt keinen Plan', async () => {
    const { parsePlan } = await import('../src/logic/parser');
    expect(parsePlan('<html><body>Anmeldung</body>', '1')).toBeNull();
    expect(parsePlan('<html><body>Anmeldung</body></html>', '1')).toBeNull();
    expect(parsePlan('', '1')).toBeNull();
  });
});

describe('Tagesplan-Funktionen', () => {
  const stunde = (nr: number, von: string, bis: string, fach = 'MAT2', kurs: string | null = 'MAT2', entfaellt = false): Lesson =>
    neueStunde({ stunde: nr, beginn: t(von), ende: t(bis), fach, raum: '226', lehrer: 'Weis', entfaellt, kursKuerzel: kurs, klasse: '12/5' });
  const plan = (...s: Lesson[]): TagesPlan => ({ kopf: { datumPlan: '', zeitstempel: '', schulnummer: '', zusatzInfo: [] }, klasse: '12/5', stunden: s, hinweise: [] });

  it('Freistunde zwischen zwei Stunden', () => {
    const p = plan(stunde(1, '07:15', '08:00'), stunde(2, '08:00', '08:45'), stunde(4, '09:50', '10:35', 'GES5', 'ges5'));
    expect(freistunden(p)).toEqual([[2, t('08:45'), t('09:50')]]);
  });

  it('normale Pause ist keine Freistunde', () => {
    expect(freistunden(plan(stunde(1, '07:15', '08:00'), stunde(2, '08:20', '09:05')))).toEqual([]);
  });

  it('Schluss ignoriert ausgefallene letzte Stunde', () => {
    const p = plan(stunde(1, '07:15', '08:00'), stunde(7, '13:40', '14:25', '---', null, true));
    expect(schluss(p)).toBe(t('08:00'));
    expect(schluss(plan())).toBeNull();
  });

  it('hatStundeVon vergleicht den Kurs', () => {
    const vorbild = stunde(1, '07:15', '08:00');
    expect(hatStundeVon(plan(stunde(3, '09:05', '09:50')), vorbild)).toBe(true);
    expect(hatStundeVon(plan(stunde(3, '09:05', '09:50', 'DEU1', 'DEU1')), vorbild)).toBe(false);
    // Fällt die Stunde aus, ist das nicht "die nächste Stunde".
    expect(hatStundeVon(plan(stunde(3, '09:05', '09:50', 'MAT2', 'MAT2', true)), vorbild)).toBe(false);
  });
});

describe('Hinweis und Stundenliste', () => {
  const std = (fach: string, info: string): Lesson =>
    neueStunde({ stunde: 1, beginn: t('07:15'), ende: t('08:00'), fach, raum: '028', lehrer: 'Ilgst', info, kursKuerzel: fach, klasse: '12/5' });
  const liste =
    'Klausur!; BIO3 Herr Lonzer fällt aus; CHE1 Herr Gruß fällt aus; DEU3 Frau Meier fällt aus; DEU4 Herr Wesenberg fällt aus; MAT2 Frau Weisheit fällt aus';

  it('Ausfälle anderer Kurse fliegen raus', () => {
    expect(hinweisKurz(std('deu1', liste))).toBe('Klausur!');
  });

  it('eigener Ausfall und Allgemeines bleiben', () => {
    expect(hinweisKurz(std('DEU3', liste))).toBe('Klausur!; DEU3 Frau Meier fällt aus');
    expect(hinweisKurz(std('MAT2', 'Aufgaben in Moodle'))).toBe('Aufgaben in Moodle');
    expect(hinweisKurz(std('MAT2', 'fällt aus'))).toBe('fällt aus');
    expect(hinweisKurz(std('MAT2', ''))).toBe('');
  });

  it('Stundenliste fasst Aufeinanderfolgende zusammen', () => {
    expect(stundenListe([5, 1, 2])).toBe('1.–2., 5. Std');
    expect(stundenListe([3])).toBe('3. Std');
    expect(stundenListe([1, 2, 3, 4, 4])).toBe('1.–4. Std');
  });
});

describe('Blöcke', () => {
  const std = (nr: number, von: string, bis: string, fach: string, lehrer = 'Got', raum = '033'): Lesson =>
    neueStunde({ stunde: nr, beginn: t(von), ende: t(bis), fach, raum, lehrer, kursKuerzel: fach, klasse: '12/5' });

  it('Doppelstunde wird ein Block', () => {
    const b = alsBloecke([std(1, '07:15', '08:00', 'MAT2'), std(2, '08:00', '08:45', 'MAT2'), std(3, '09:05', '09:50', 'DEU1')]);
    expect(b.map(blockStundenText)).toEqual(['1.–2. Std', '3. Std']);
    expect([blockZusammengefasst(b[0]).beginn, blockZusammengefasst(b[0]).ende]).toEqual([t('07:15'), t('08:45')]);
    expect(b.map((x) => x.ersteIndex)).toEqual([0, 2]);
    expect(blockStundenKurz(b[0])).toBe('1.–2.');
  });

  it('große Pause oder andere Details trennen', () => {
    // 2. → 3. Stunde mit 20 Minuten Pause: kein Block.
    expect(alsBloecke([std(2, '08:00', '08:45', 'MAT2'), std(3, '09:05', '09:50', 'MAT2')])).toHaveLength(2);
    // Anderer Raum oder andere Lehrkraft: getrennt.
    expect(alsBloecke([std(1, '07:15', '08:00', 'A'), std(2, '08:00', '08:45', 'A', 'Got', '034')])).toHaveLength(2);
    expect(alsBloecke([std(1, '07:15', '08:00', 'A'), std(2, '08:00', '08:45', 'A', 'Mei')])).toHaveLength(2);
    // Lücke in den Nummern: getrennt.
    expect(alsBloecke([std(1, '07:15', '08:00', 'A'), std(3, '08:00', '08:45', 'A')])).toHaveLength(2);
  });

  it('parallele Kurse werden einzeln verfolgt', () => {
    const b = alsBloecke([
      std(1, '07:15', '08:00', 'DEU1'),
      std(1, '07:15', '08:00', 'MAT2'),
      std(2, '08:00', '08:45', 'DEU1'),
      std(2, '08:00', '08:45', 'MAT2')
    ]);
    expect(b.map(blockStundenText)).toEqual(['1.–2. Std', '1.–2. Std']);
    expect(b.map((x) => x.stunden[0].fach)).toEqual(['DEU1', 'MAT2']);
  });

  it('Klausur wird ein Block, auch über Pausen', () => {
    const klausur = (nr: number, von: string, bis: string, info: string): Lesson => ({ ...std(nr, von, bis, 'ENG2', 'Däu', '032'), istKlausur: true, info });
    const b = alsBloecke([
      klausur(3, '09:05', '09:50', 'Klausur!'),
      klausur(4, '09:50', '10:35', 'Klausur!'),
      klausur(5, '11:00', '11:45', 'ENG2 Klausur'),
      klausur(6, '11:45', '12:30', 'Klausur')
    ]);
    expect(b.map(blockStundenText)).toEqual(['3.–6. Std']);
    expect([blockZusammengefasst(b[0]).beginn, blockZusammengefasst(b[0]).ende]).toEqual([t('09:05'), t('12:30')]);
    const getrennt = alsBloecke([klausur(3, '09:05', '09:50', 'K'), { ...klausur(4, '09:50', '10:35', 'K'), raum: '033' }]);
    expect(getrennt).toHaveLength(2);
  });
});

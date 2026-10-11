import { describe, expect, it } from 'vitest';
import {
  anwesenheit,
  anwesenheitText,
  belegt,
  freiBloecke,
  freiVor,
  freiblockStundenText,
  gemeinsamFrei,
  gemeinsamFreiAlle,
  gemeinsameStunden,
  inDerSchule,
  jahrgang,
  jetztFrei,
  zusammenKey
} from '../src/logic/freizeit';
import { type Lesson, type TagesPlan, neueStunde } from '../src/logic/models';
import type { Zeitfenster } from '../src/logic/raster';
import { parseUhrzeit } from '../src/logic/zeit';

const t = (s: string) => parseUhrzeit(s)!;

function stunde(nr: number, von: string, bis: string, kurs: string, entfaellt = false): Lesson {
  return neueStunde({ stunde: nr, beginn: t(von), ende: t(bis), fach: kurs, entfaellt, kursKuerzel: kurs, klasse: '12/5' });
}

const plan = (...s: Lesson[]): TagesPlan => ({ kopf: { datumPlan: '', zeitstempel: '', schulnummer: '', zusatzInfo: [] }, klasse: '12/5', stunden: s, hinweise: [] });
const zeiten = (b: { beginn: number; ende: number }[]) => b.map((x) => [x.beginn, x.ende]);

// Raster wie an der Schule aus dem Screenshot: 1. 07:15, 2. 08:00, Pause, 3. 09:05, 4. 09:50 …
const ich = plan(
  stunde(1, '07:15', '08:00', 'MAT2'),
  stunde(2, '08:00', '08:45', 'MAT2'),
  stunde(5, '11:00', '11:45', 'ges5'),
  stunde(6, '11:45', '12:30', 'ges5')
);

const raster: Zeitfenster[] = [
  { stunde: 1, beginn: t('07:15'), ende: t('08:00') },
  { stunde: 2, beginn: t('08:00'), ende: t('08:45') },
  { stunde: 3, beginn: t('09:05'), ende: t('09:50') },
  { stunde: 4, beginn: t('09:50'), ende: t('10:35') },
  { stunde: 5, beginn: t('11:00'), ende: t('11:45') },
  { stunde: 6, beginn: t('11:45'), ende: t('12:30') },
  { stunde: 7, beginn: t('12:45'), ende: t('13:30') }
];

describe('Freizeit', () => {
  it('gemeinsame Freistunde', () => {
    const freund = plan(stunde(1, '07:15', '08:00', 'MAT2'), stunde(2, '08:00', '08:45', 'MAT2'), stunde(3, '09:05', '09:50', 'DEU1'), stunde(6, '11:45', '12:30', 'ENG2'));
    // Ich: frei 08:45–11:00; Freund: frei 09:50–11:45 → gemeinsam 09:50–11:00.
    expect(zeiten(gemeinsamFrei(ich, freund))).toEqual([[t('09:50'), t('11:00')]]);
    expect(gemeinsameStunden(ich, freund).map((l) => l.stunde)).toEqual([1, 2]);
  });

  it('Ausfall macht frei', () => {
    const freund = plan(stunde(1, '07:15', '08:00', 'BIO3'), stunde(3, '09:05', '09:50', 'BIO3', true), stunde(6, '11:45', '12:30', 'BIO3'));
    expect(zeiten(gemeinsamFrei(ich, freund))).toEqual([[t('08:45'), t('11:00')]]);
  });

  it('nur wenn beide in der Schule sind', () => {
    const freund = plan(stunde(5, '11:00', '11:45', 'SPO'), stunde(6, '11:45', '12:30', 'SPO'));
    expect(gemeinsamFrei(ich, freund)).toEqual([]);
  });

  it('kurze Pausen zählen nicht', () => {
    const a = plan(stunde(1, '07:15', '08:00', 'A'), stunde(2, '08:20', '09:05', 'A'));
    const b = plan(stunde(1, '07:15', '08:00', 'B'), stunde(2, '08:20', '09:05', 'B'));
    expect(gemeinsamFrei(a, b)).toEqual([]);
  });

  it('ohne Unterricht keine gemeinsame Zeit', () => {
    expect(gemeinsamFrei(ich, plan())).toEqual([]);
    expect(belegt(plan())).toEqual([]);
    expect(belegt(ich)).toEqual([
      [t('07:15'), t('08:45')],
      [t('11:00'), t('12:30')]
    ]);
  });

  it('eigene Freistunden mit Stundennummer', () => {
    const spaet = plan(stunde(3, '09:05', '09:50', 'DEU1'), stunde(6, '11:45', '12:30', 'ENG2'));
    const b = freiBloecke(spaet, raster);
    // 1.–2. Std vor Unterrichtsbeginn, 4.–5. Std Lücke; nach der 6. nichts mehr.
    expect(b.map(freiblockStundenText)).toEqual(['1.–2. Std', '4.–5. Std']);
    expect([b[0].beginn, b[0].ende]).toEqual([t('07:15'), t('09:05')]);
    expect([b[1].beginn, b[1].ende]).toEqual([t('09:50'), t('11:45')]);
  });

  it('Ausfall steht in der Liste und ist keine Freistunde', () => {
    const p = plan(stunde(1, '07:15', '08:00', 'A', true), stunde(2, '08:00', '08:45', 'A'));
    expect(freiBloecke(p, raster)).toEqual([]);
    expect(freiBloecke(p, raster, true).map(freiblockStundenText)).toEqual(['1. Std']);
  });

  it('gemeinsam frei am Tagesanfang', () => {
    const a = plan(stunde(2, '08:00', '08:45', 'A'), stunde(3, '09:05', '09:50', 'A'));
    const b = plan(stunde(2, '08:00', '08:45', 'B'), stunde(5, '11:00', '11:45', 'B'));
    expect(gemeinsamFrei(a, b, raster).map(freiblockStundenText)).toEqual(['1. Std']);
  });

  it('nach Schluss zählt nicht', () => {
    const a = plan(stunde(1, '07:15', '08:00', 'A'), stunde(2, '08:00', '08:45', 'A'));
    const b = plan(stunde(1, '07:15', '08:00', 'B'), stunde(5, '11:00', '11:45', 'B'));
    expect(gemeinsamFrei(a, b, raster)).toEqual([]);
  });

  it('gemeinsam frei mit Raster: Mitte und Ausfall', () => {
    const freund = plan(stunde(1, '07:15', '08:00', 'BIO3'), stunde(3, '09:05', '09:50', 'BIO3', true), stunde(6, '11:45', '12:30', 'BIO3'));
    // Ich frei 3.–4., Freund frei 2.–5. → gemeinsam 3.–4. inkl. Pausen 08:45–11:00.
    const b = gemeinsamFrei(ich, freund, raster);
    expect(b.map(freiblockStundenText)).toEqual(['3.–4. Std']);
    expect([b[0].beginn, b[0].ende]).toEqual([t('08:45'), t('11:00')]);
  });

  it('jetzt frei: nur in der Lücke und nicht nach Schluss', () => {
    // Ich: Unterricht 1., 2., 5., 6. Std – frei 08:45–11:00 (Pausen eingerechnet).
    expect(freiblockStundenText(jetztFrei(ich, raster, t('09:30'))!)).toBe('3.–4. Std');
    expect(freiblockStundenText(jetztFrei(ich, raster, t('08:50'))!)).toBe('3.–4. Std');
    expect(jetztFrei(ich, raster, t('08:30'))).toBeNull(); // 2. Std läuft
    expect(jetztFrei(ich, raster, t('11:00'))).toBeNull(); // 5. Std beginnt
    expect(jetztFrei(ich, raster, t('12:40'))).toBeNull(); // Schule aus
    expect(jetztFrei(plan(), raster, t('09:30'))).toBeNull(); // kein Unterricht = nicht da
  });

  it('jetzt frei: Ausfall zählt als frei', () => {
    const p = plan(stunde(1, '07:15', '08:00', 'A'), stunde(3, '09:05', '09:50', 'A', true), stunde(5, '11:00', '11:45', 'A'));
    expect(jetztFrei(p, raster, t('09:20'))?.ende).toBe(t('11:00'));
  });

  it('gemeinsam frei mit drei Leuten', () => {
    const b = plan(stunde(1, '07:15', '08:00', 'MAT2'), stunde(2, '08:00', '08:45', 'MAT2'), stunde(3, '09:05', '09:50', 'DEU1'), stunde(6, '11:45', '12:30', 'ENG2'));
    const c = plan(stunde(1, '07:15', '08:00', 'X'), stunde(2, '08:00', '08:45', 'X'), stunde(5, '11:00', '11:45', 'Y'), stunde(6, '11:45', '12:30', 'Y'));
    // ich frei 3.–4., b frei 4.–5., c frei 3.–4. → alle drei nur in der 4. Stunde.
    const mitRaster = gemeinsamFreiAlle([ich, b, c], raster);
    expect(mitRaster.map(freiblockStundenText)).toEqual(['4. Std']);
    expect([mitRaster[0].beginn, mitRaster[0].ende]).toEqual([t('09:50'), t('11:00')]);
    // Ohne Raster dasselbe Fenster als reine Uhrzeit.
    expect(zeiten(gemeinsamFreiAlle([ich, b, c]))).toEqual([[t('09:50'), t('11:00')]]);
    // Wer nicht in der Schule ist, nimmt allen die gemeinsame Zeit.
    expect(gemeinsamFreiAlle([ich, b, plan()], raster)).toEqual([]);
    expect(gemeinsamFreiAlle([], raster)).toEqual([]);
  });

  it('gleicher Kurs in anderen Klassen ist zusammen', () => {
    const meins = plan({ ...stunde(3, '09:05', '09:50', 'DEU1'), lehrer: 'Got', unterrichtsNr: '11' });
    // Derselbe Kurs, von einem Freund aus Klasse 12/6 gewählt: andere Klasse, andere Unterrichtsnummer.
    const seins = plan({ ...stunde(3, '09:05', '09:50', 'DEU1'), klasse: '12/6', lehrer: 'Got', unterrichtsNr: '42' });
    expect(gemeinsameStunden(meins, seins).map((l) => l.stunde)).toEqual([3]);
    expect(zusammenKey(meins.stunden[0])).toBe(zusammenKey(seins.stunden[0]));
  });

  it('anderer Lehrer oder andere Zeit ist nicht zusammen', () => {
    const meins = plan({ ...stunde(3, '09:05', '09:50', 'DEU1'), lehrer: 'Got' });
    const andererLehrer = plan({ ...stunde(3, '09:05', '09:50', 'DEU1'), klasse: '12/6', lehrer: 'Mei' });
    const andereZeit = plan({ ...stunde(4, '09:50', '10:35', 'DEU1'), klasse: '12/6', lehrer: 'Got' });
    expect(gemeinsameStunden(meins, andererLehrer)).toEqual([]);
    expect(gemeinsameStunden(meins, andereZeit)).toEqual([]);
  });

  it('jetzt frei: vor der ersten Stunde ist man noch nicht da', () => {
    const spaet = plan(stunde(3, '09:05', '09:50', 'DEU1'), stunde(5, '11:00', '11:45', 'ENG2'));
    expect(jetztFrei(spaet, raster, t('07:20'))).toBeNull();
    expect(freiblockStundenText(jetztFrei(spaet, raster, t('10:00'))!)).toBe('4. Std');
    // 1.–2. fällt aus: Unterricht beginnt erst mit der 3. – davor nicht "frei".
    const ausfallZuerst = plan(stunde(1, '07:15', '08:00', 'A', true), stunde(2, '08:00', '08:45', 'A', true), stunde(3, '09:05', '09:50', 'A'), stunde(5, '11:00', '11:45', 'A'));
    expect(jetztFrei(ausfallZuerst, raster, t('07:30'))).toBeNull();
    expect(inDerSchule(spaet, t('10:00'))).toBe(true);
    expect(inDerSchule(spaet, t('08:00'))).toBe(false);
    expect(inDerSchule(spaet, t('11:45'))).toBe(false);
  });

  it('ohne Raster zählt Ausfall als frei, wenn gewünscht', () => {
    const p = plan(stunde(1, '07:15', '08:00', 'A'), stunde(2, '08:00', '08:45', 'A', true), stunde(3, '08:45', '09:30', 'A'));
    expect(freiBloecke(p, [])).toEqual([]);
    expect(zeiten(freiBloecke(p, [], true))).toEqual([[t('08:00'), t('08:45')]]);
    // Lehrkraft mit 1. und (ausgefallener) 5. Stunde: keine Lücke, der Tag endet nach der 1.
    const q = plan(stunde(1, '07:15', '08:00', 'A'), stunde(5, '11:00', '11:45', 'A', true));
    expect(freiBloecke(q, [], true)).toEqual([]);
  });

  it('gleiches Kürzel in anderem Jahrgang ist nicht zusammen', () => {
    const elf = plan({ ...stunde(5, '11:00', '11:45', 'SPO1'), klasse: '11' });
    const zwoelf = plan({ ...stunde(5, '11:00', '11:45', 'SPO1'), klasse: '12' });
    expect(gemeinsameStunden(elf, zwoelf)).toEqual([]);
    expect(jahrgang('12/5')).toBe('12');
    expect(jahrgang('12a')).toBe('12');
    expect(jahrgang('Q1/2')).toBe('Q1');
  });

  it('abweichende Klassenzeiten: echte Stunden zählen statt Raster', () => {
    // Raster: 4. 09:50–10:35, 5. 11:00–11:45. Die Klasse hat die 5. Stunde aber 10:40–11:25.
    const p = plan(stunde(3, '09:05', '09:50', 'A'), stunde(5, '10:40', '11:25', 'A'));
    const bl = freiBloecke(p, raster);
    const block = bl[bl.length - 1];
    expect(freiblockStundenText(block)).toBe('4. Std');
    expect([block.beginn, block.ende]).toEqual([t('09:50'), t('10:40')]);
    // Um 10:50 sitzt man im Unterricht – nicht "gerade frei bis 11:00".
    expect(jetztFrei(p, raster, t('10:50'))).toBeNull();
  });

  it('Freistunde steht vor der richtigen Stunde', () => {
    // Raster sagt 3. Stunde ab 09:05, die Klasse beginnt sie schon um 09:00.
    const p = plan(stunde(3, '09:00', '09:45', 'A'), stunde(4, '09:50', '10:35', 'A'));
    const vor = freiVor(p, raster);
    expect([...vor.keys()]).toEqual([0]);
    expect(freiblockStundenText(vor.get(0)!)).toBe('1.–2. Std');
    expect(vor.get(0)!.ende).toBe(t('09:00'));
  });

  it('Anwesenheit vorher, dann, nachher', () => {
    // ich: 07:15–12:30
    expect(anwesenheit(ich, t('07:00')).art).toBe('NOCH_NICHT');
    expect(anwesenheitText(anwesenheit(ich, t('07:00')))).toBe('kommt um 07:15');
    expect(anwesenheit(ich, t('09:00')).art).toBe('DA');
    expect(anwesenheitText(anwesenheit(ich, t('09:00')))).toBe('in der Schule bis 12:30');
    expect(anwesenheit(ich, t('12:30')).art).toBe('SCHON_WEG');
    expect(anwesenheitText(anwesenheit(ich, t('13:00')))).toBe('schon fertig (seit 12:30)');
    expect(anwesenheit(plan(), t('09:00')).art).toBe('KEIN_UNTERRICHT');
    // Ausfall der letzten Stunden verkürzt den Tag.
    const kurz = plan(stunde(1, '07:15', '08:00', 'A'), stunde(6, '11:45', '12:30', 'A', true));
    expect(anwesenheit(kurz, t('09:00')).art).toBe('SCHON_WEG');
  });
});

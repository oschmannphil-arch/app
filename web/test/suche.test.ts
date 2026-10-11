import { describe, expect, it } from 'vitest';
import { ersterSchultag, raumGruppe, schultagVersetzt, wochenReferenz } from '../src/logic/raster';
import { freiblockStundenText } from '../src/logic/freizeit';
import { neueStunde } from '../src/logic/models';
import { SchulTag, lehrer, lehrerVon, raeumeVon, raum, steckbriefAus, trefferSchluessel } from '../src/logic/suche';
import { parseUhrzeit } from '../src/logic/zeit';
import { plan } from './helfer';

const t = (s: string) => parseUhrzeit(s)!;
const dienstag = '2026-10-06';

const std = (nr: number, von: string, bis: string, fach: string, le: string, ra: string, info = '') =>
  `<Std><St>${nr}</St><Beginn>${von}</Beginn><Ende>${bis}</Ende><Fa>${fach}</Fa><Le>${le}</Le><Ra>${ra}</Ra><Nr></Nr><If>${info}</If></Std>`;

/** Zwei Klassen; Weis unterrichtet in beiden, Raum 226 ist in der 3. Stunde frei. */
const schultag = () =>
  new SchulTag(
    dienstag,
    plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>MAT2</KKz></Ku><Ku><KKz>PHY1</KKz></Ku></Kurse><Pl>
${std(1, '07:15', '08:00', 'MAT2', 'Weis', '226')}
${std(2, '08:00', '08:45', 'MAT2', 'Weis', '226')}
${std(3, '09:05', '09:50', 'PHY1', 'Luth', '235')}
${std(4, '09:50', '10:35', '---', '---', '', 'PHY1 fällt aus')}
</Pl></Kl>
<Kl><Kurz>10/2</Kurz><Kurse><Ku><KKz>MA</KKz></Ku></Kurse><Pl>
${std(4, '09:50', '10:35', 'MA', 'Weis', '121')}
${std(5, '10:55', '11:40', 'DE', 'Schö', '204 205')}
</Pl></Kl>
</Klassen></WplanVp>`)
  );

describe('Suche', () => {
  it('findet Lehrer und Räume, exakte Treffer zuerst', () => {
    const s = schultag();
    expect(s.suche('weis')).toEqual([lehrer('Weis')]);
    expect(s.suche('226')).toEqual([raum('226')]);
    // "2" passt auf mehrere Räume; die mit "2" anfangen, kommen vor denen, die "2" nur enthalten.
    expect(s.suche('2').map((x) => x.name)).toEqual(['204', '205', '226', '235', '121']);
    expect(s.suche('   ')).toEqual([]);
  });

  it('mehrere Räume mit Leerzeichen werden getrennt', () => {
    expect(raeumeVon('204 205')).toEqual(['204', '205']);
    expect(raeumeVon('SH 1')).toEqual(['SH 1']);
    expect(raeumeVon('TH1, TH2')).toEqual(['TH1', 'TH2']);
    expect(raeumeVon('---')).toEqual([]);
    expect(lehrerVon('Got Mei')).toEqual(['Got', 'Mei']);
    expect(lehrerVon('---')).toEqual([]);
  });

  it('was macht Weis gerade', () => {
    const s = schultag();
    const weis = lehrer('Weis');
    const jetzt = s.belegung(weis, t('08:10'));
    expect(jetzt.art).toBe('JETZT');
    if (jetzt.art === 'JETZT') expect(jetzt.lesson.raum).toBe('226');

    // In der Pause nach der 2. Stunde: frei, als Nächstes 4. Stunde in 121 (andere Klasse).
    const frei = s.belegung(weis, t('08:50'));
    expect(frei.art).toBe('FREI');
    if (frei.art === 'FREI') expect(frei.naechste?.raum).toBe('121');

    const spaet = s.belegung(weis, t('12:00'));
    expect(spaet.art === 'FREI' && spaet.naechste === null).toBe(true);
  });

  it('freie Räume: am längsten frei zuerst', () => {
    const s = schultag();
    // 09:10 (3. Stunde): 235 belegt; 226 heute nicht mehr belegt; 121 ab 09:50; 204/205 ab 10:55.
    const frei = s.freieRaeume(t('09:10'))!;
    expect(frei.map((x) => x[0])).toEqual(['226', '204', '205', '121']);
    expect(frei[0][1]).toBeNull();
    // Vor Unterrichtsbeginn und danach gibt es keine sinnvolle Aussage.
    expect(s.freieRaeume(t('06:00'))).toBeNull();
    expect(s.freieRaeume(t('15:00'))).toBeNull();
  });

  it('Tagesablauf eines Raums mit freien Stunden', () => {
    const zeilen = schultag().tagesablauf(raum('226'));
    expect(zeilen.map((z) => z[0].stunde)).toEqual([1, 2, 3, 4, 5]);
    expect(zeilen.map((z) => z[1].length)).toEqual([1, 1, 0, 0, 0]);
    expect(zeilen[2][0].beginn).toBe(t('09:05'));
  });

  it('Tagesablauf eines Lehrers ohne leere Ränder', () => {
    expect(schultag().tagesablauf(lehrer('Weis')).map((z) => z[0].stunde)).toEqual([1, 2, 3, 4]);
  });

  it('Klausuraufsicht ist nicht frei', () => {
    const liste = 'Klausur!; DEU3 Frau Meier fällt aus';
    const s = new SchulTag(
      dienstag,
      plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku></Kurse><Pl>
${std(1, '07:15', '08:00', 'DEU1', 'Got', '033', liste)}
${std(1, '07:15', '08:00', 'DEU3', 'Mei', '031', liste)}
</Pl></Kl></Klassen></WplanVp>`)
    );
    // Got beaufsichtigt die Klausur in 033 …
    const got = s.belegung(lehrer('Got'), t('07:30'));
    expect(got.art).toBe('JETZT');
    if (got.art === 'JETZT') expect(got.lesson.istKlausur).toBe(true);
    // … Meiers Kurs fällt dagegen wirklich aus.
    expect(s.belegung(lehrer('Mei'), t('07:30')).art).toBe('FREI');
  });

  it('Schultage überspringen das Wochenende', () => {
    expect(schultagVersetzt('2026-10-09', 1)).toBe('2026-10-12');
    expect(schultagVersetzt('2026-10-12', -1)).toBe('2026-10-09');
    expect(ersterSchultag('2026-10-10')).toBe('2026-10-12');
    expect(ersterSchultag(dienstag)).toBe(dienstag);
  });

  it('Lehrer: Lücken und Unterricht', () => {
    const s = schultag();
    // Weis unterrichtet 1., 2. (12/5) und 4. (10/2); dazwischen ist die 3. Stunde frei.
    expect(s.unterrichtsStunden(lehrer('Weis'))).toEqual([1, 2, 4]);
    expect(s.luecken(lehrer('Weis')).map(freiblockStundenText)).toEqual(['3. Std']);
    // Luth: nur die 3. Stunde – davor ist nicht "Lücke", sondern noch nicht da.
    expect(s.luecken(lehrer('Luth'))).toEqual([]);
    expect(s.luecken(lehrer('gibtEsNicht'))).toEqual([]);
  });

  it('Raumgruppen zum Filtern', () => {
    expect(raumGruppe('204')).toBe('Etage 2');
    expect(raumGruppe('033')).toBe('EG');
    expect(raumGruppe('SH 1')).toBe('SH');
    expect(raumGruppe('A101')).toBe('A · Etage 1');
    expect(raumGruppe('12')).toBe('Sonstige');
    expect(raumGruppe('Aula')).toBe('Sonstige');
    expect(raumGruppe('Turnhalle')).toBe('Sonstige');
    expect(trefferSchluessel(lehrer('Weis'))).toBe('L:Weis');
    expect(trefferSchluessel(raum('204'))).toBe('R:204');
  });

  it('Wochenreferenz springt am Wochenende', () => {
    const samstag = '2026-10-10';
    expect(wochenReferenz(0, samstag)).toBe('2026-10-12');
    expect(wochenReferenz(1, samstag)).toBe('2026-10-19');
    expect(wochenReferenz(-1, samstag)).toBe('2026-10-05');
    expect(wochenReferenz(0, dienstag)).toBe(dienstag);
  });

  it('freie Räume zu Stunden', () => {
    const s = schultag();
    // 226: Std 1, 2 belegt; 235: nur Std 3; 121: nur Std 4; 204/205: nur Std 5.
    expect(s.freieRaeumeIn(new Set([3])).filter((r) => r !== '235')).toEqual(['121', '204', '205', '226']);
    expect(s.freieRaeumeIn(new Set([3]))).not.toContain('235');
    expect(s.freieRaeumeIn(new Set([1, 3]))).not.toContain('226'); // in der 1. belegt
    expect(s.freieRaeumeIn(new Set([3, 4]))).toContain('226'); // 3. und 4. beide frei
    expect(s.freieRaeumeIn(new Set([3, 4]))).not.toContain('121'); // in der 4. belegt
    expect(s.freieRaeumeIn(new Set())).toEqual([]);
  });

  it('Lehrkraft mit nur Ausfällen', () => {
    const s = new SchulTag(
      dienstag,
      plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>MAT2</KKz></Ku></Kurse><Pl>
${std(1, '07:15', '08:00', 'MAT2', 'Weis', '226', 'fällt aus')}
${std(2, '08:00', '08:45', 'MAT2', 'Weis', '226', 'fällt aus')}
${std(3, '09:05', '09:50', 'MAT2', 'Mei', '226')}
</Pl></Kl></Klassen></WplanVp>`)
    );
    expect(s.faelltGanzAus(lehrer('Weis'))).toBe(true);
    expect(s.faelltGanzAus(lehrer('Mei'))).toBe(false);
    expect(s.faelltGanzAus(lehrer('Unbekannt'))).toBe(false); // nicht im Plan ≠ fällt aus
  });

  it('Steckbrief trennt Räume in einem Eintrag', () => {
    const l = neueStunde({ stunde: 1, fach: 'DE', raum: '204 205', lehrer: 'Schö', klasse: '10/2' });
    expect(steckbriefAus([l])!.raeume).toEqual(['204', '205']);
  });

  it('Steckbrief fasst den Lehrer-Tag zusammen', () => {
    const s = schultag();
    const b = steckbriefAus(s.stundenVon(lehrer('Weis')))!;
    expect(b.faecher).toEqual(['MA', 'MAT2']);
    expect(b.jahrgaenge).toEqual(['10', '12']);
    expect(b.raeume).toEqual(['121', '226']);
    expect(b.stunden).toBe(3);
    expect(steckbriefAus([])).toBeNull();
  });
});

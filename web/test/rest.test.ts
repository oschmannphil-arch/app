import { describe, expect, it } from 'vitest';
import { berechneUebersicht, planKlausurAlsPruefung, planKlausurenAusStunden, pruefungIstVorbei, type Pruefung } from '../src/logic/aufgaben';
import { FachName } from '../src/logic/fachname';
import { ferienText } from '../src/logic/ferien';
import { hatAenderung, mitNachfolgern, pruefeKurse } from '../src/logic/kursmigration';
import { lesen, nachricht, androidLink, webLink } from '../src/logic/freundteilen';
import { neueStunde } from '../src/logic/models';
import {
  Abi,
  type Note,
  alsSchulnote,
  benoetigt,
  durchschnittAllerHalbjahre,
  fachSchnitt,
  gesamtSchnitt,
  halbjahr,
  halbjahrDavor,
  halbjahrGrenzen,
  nachFach,
  prognose,
  runden,
  systemAusKursIds,
  zielErreicht,
  zeugnis
} from '../src/logic/noten';
import { mitSymbol, symbolFuer } from '../src/logic/symbole';
import { countdownText, dauerText, parseUhrzeit, uhrzeitText } from '../src/logic/zeit';

const t = (s: string) => parseUhrzeit(s)!;
const MIN = 60_000;

describe('Zeit', () => {
  it('Countdown', () => {
    // Läuft seit 10 Minuten, noch 35 Minuten.
    expect(countdownText(-10 * MIN, 35 * MIN)).toBe('läuft noch 35 Min');
    expect(countdownText(-10 * MIN, 35 * MIN, 'noch')).toBe('noch 35 Min');
    expect(countdownText(-44 * MIN, 30_000)).toBe('läuft noch 1 Min');
    // Aufrunden: 8 Min 20 s → 9 Min.
    expect(countdownText(8 * MIN + 20_000, 45 * MIN)).toBe('in 9 Min');
    expect(countdownText(80 * MIN, null)).toBe('in 1 Std 20 Min');
    expect(countdownText(0, null)).toBe('jetzt');
    expect(countdownText(-1, null)).toBeNull(); // vorbei, Ende unbekannt
    expect(countdownText(-1, -1)).toBeNull(); // vorbei
    expect(countdownText(601 * MIN, null)).toBeNull(); // zu weit weg
  });

  it('Dauer und Uhrzeit', () => {
    expect(dauerText(12)).toBe('12 Min');
    expect(dauerText(120)).toBe('2 Std');
    expect(dauerText(80)).toBe('1 Std 20 Min');
    expect(uhrzeitText(t('7:05'))).toBe('07:05');
    expect(parseUhrzeit('25:00')).toBeNull();
    expect(parseUhrzeit('abc')).toBeNull();
  });
});

describe('Prüfungen', () => {
  const heute = '2026-10-05';
  const klausur = (tag: string, ende: string | null): Pruefung => ({
    id: 'x', fach: 'DEU1', titel: '', datum: tag, art: 'KLAUSUR', notiz: '', endeSekunden: ende === null ? -1 : t(ende) * 60
  });

  it('Klausur ist nach ihrem Ende vorbei', () => {
    const k = klausur(heute, '08:45');
    expect(pruefungIstVorbei(k, heute, t('08:00'))).toBe(false);
    expect(pruefungIstVorbei(k, heute, t('16:06'))).toBe(true);
    expect(pruefungIstVorbei(klausur('2026-10-06', '08:45'), heute, t('23:00'))).toBe(false);
    expect(pruefungIstVorbei(klausur('2026-10-04', null), heute, t('00:01'))).toBe(true);
  });

  it('ohne Ende bleibt der Tag bis Mitternacht', () => {
    expect(pruefungIstVorbei(klausur(heute, null), heute, t('23:59'))).toBe(false);
  });

  it('Plan-Klausur merkt sich das Ende', () => {
    const l = neueStunde({ stunde: 1, beginn: t('07:15'), ende: t('08:45'), fach: 'DEU1', info: 'Klausur!', kursKuerzel: 'DEU1', klasse: '12/5', istKlausur: true });
    const k = planKlausurenAusStunden(heute, [l]);
    expect(k).toHaveLength(1);
    expect(planKlausurAlsPruefung(k[0]).endeSekunden).toBe(t('08:45') * 60);
  });

  it('Übersicht zählt offene, überfällige und anstehende', () => {
    const u = berechneUebersicht(
      [
        { id: '1', fach: 'M', text: 'a', faellig: '2026-10-04', erledigt: false, erstelltAm: 0 },
        { id: '2', fach: 'M', text: 'b', faellig: '2026-10-06', erledigt: false, erstelltAm: 0 },
        { id: '3', fach: 'M', text: 'c', faellig: null, erledigt: false, erstelltAm: 0 },
        { id: '4', fach: 'M', text: 'd', faellig: '2026-10-05', erledigt: true, erstelltAm: 0 }
      ],
      [klausur('2026-10-30', null), klausur('2026-10-08', null)],
      heute,
      t('10:00')
    );
    expect(u.offeneAufgaben).toBe(3);
    expect(u.ueberfaellig).toBe(1);
    expect(u.bisMorgen).toBe(1);
    expect(u.naechstePruefung?.datum).toBe('2026-10-08');
  });
});

describe('Freunde teilen', () => {
  const basis = 'https://nutzer.github.io/app/';

  it('Link übersteht Hin und Zurück (Web und Android)', () => {
    const kurse = ['12/5::DEU1', '12/5::*', '10/2::MA'];
    for (const link of [webLink(basis, 'Zoë Müller', kurse), androidLink('Zoë Müller', kurse)]) {
      const f = lesen(link)!;
      expect(f.name).toBe('Zoë Müller');
      expect([...f.kurse].sort()).toEqual([...kurse].sort());
    }
  });

  it('Link steckt im Nachrichtentext', () => {
    const text = nachricht(basis, 'Anna', ['12/5::MAT2']);
    const f = lesen(`Hey!\n${text}\nBis morgen`)!;
    expect(f.name).toBe('Anna');
    expect(f.kurse).toEqual(['12/5::MAT2']);
  });

  it('ohne Namen geht auch', () => {
    expect(lesen(webLink(basis, '', ['12/5::MAT2']))!.name).toBe('');
  });

  it('Ungültiges wird abgelehnt', () => {
    expect(lesen(null)).toBeNull();
    expect(lesen('https://example.com')).toBeNull();
    expect(lesen('nextlesson://freund?name=A&kurse=')).toBeNull();
    // Einträge, die keine Kurs-ID sind, fliegen raus.
    expect(lesen('nextlesson://freund?name=A&kurse=quatsch')).toBeNull();
    expect(lesen('nextlesson://freund?name=A&kurse=quatsch,12%2F5%3A%3AMAT2')!.kurse).toEqual(['12/5::MAT2']);
  });

  it('Satzzeichen am Ende gehören nicht zum Link', () => {
    const link = androidLink('A', ['12/5::MAT2']);
    expect(lesen(`(${link})`)!.kurse).toEqual(['12/5::MAT2']);
    expect(lesen(`${link}.`)!.kurse).toEqual(['12/5::MAT2']);
    expect(lesen(androidLink('A', ['12/5::*']) + '!')!.kurse).toEqual(['12/5::*']);
    expect(lesen(`${webLink(basis, 'A', ['12/5::MAT2'])}.`)!.kurse).toEqual(['12/5::MAT2']);
  });

  it('Form mit Schrägstrich', () => {
    expect(lesen('nextlesson://freund/?name=A&kurse=12%2F5%3A%3AMAT2')!.kurse).toEqual(['12/5::MAT2']);
  });

  it('Nachricht nennt, wessen Kurse', () => {
    expect(nachricht(basis, 'Anna', ['12/5::MAT2']).startsWith('Meine Kurse')).toBe(true);
    expect(nachricht(basis, 'Anna', ['12/5::MAT2'], false).startsWith('Die Kurse von Anna')).toBe(true);
  });
});

describe('Fach-Namen und Symbole', () => {
  it('Oberstufenkurs und normales Fach sind dasselbe', () => {
    expect(FachName.schluessel('Geo')).toBe(FachName.schluessel('GEO2'));
    expect(FachName.schluessel('Geo 2')).toBe('geo');
    expect(FachName.schluessel('spo2_3JU')).toBe('spo');
    expect(FachName.schluessel('Mathe LK')).toBe('mathe');
  });

  it('zusammenfassen bevorzugt Namen ohne Ziffer', () => {
    expect(FachName.zusammen(['GEO2', 'Geo', 'MAT2', '', '—'])).toEqual(['Geo', 'MAT2']);
    expect(FachName.zusammen(['ENG2', 'eng1'])).toEqual(['ENG2']);
  });

  it('Symbole für Kürzel und Namen', () => {
    expect(symbolFuer('Ma')).toBe('🔢');
    expect(symbolFuer('Mathematik')).toBe('🔢');
    expect(symbolFuer('Deutsch GK')).toBe('📖');
    expect(symbolFuer('D1')).toBe('📖');
    expect(symbolFuer('Sport')).toBe('⚽');
    expect(symbolFuer('Spanisch')).toBe('🇪🇸');
    expect(symbolFuer('Spo')).toBe('⚽');
    for (const g of ['Ge', 'GE1', 'Ges', 'GES1', 'Gesch', 'Geschichte', 'Gsch']) expect(symbolFuer(g), g).toBe('📜');
    expect(symbolFuer('—')).toBeNull();
    expect(symbolFuer('Quatsch')).toBeNull();
    expect(mitSymbol('Quatsch')).toBe('Quatsch');
    expect(mitSymbol('Ma')).toBe('🔢 Ma');
  });
});

describe('Ferien', () => {
  it('Countdown und laufende Ferien', () => {
    expect(ferienText('2026-10-11')).toBe('Noch 8 Tage bis zu den Herbstferien (19.10.)');
    expect(ferienText('2026-10-18')).toBe('Morgen beginnen die Herbstferien');
    expect(ferienText('2026-10-19')).toBe('Herbstferien: noch bis 30.10.');
    expect(ferienText('2026-10-31')).toBe('Noch 51 Tage bis zu den Weihnachtsferien (21.12.)');
    expect(ferienText('2027-09-01')).toBeNull();
    expect(ferienText('2027-04-01')).toBeNull();
  });
});

describe('Kurs-Migration', () => {
  const k = (kuerzel: string) => ({ klasse: '12/5', kuerzel, fach: kuerzel, lehrer: 'X' });
  const liste = [k('MAT3'), k('ENG1'), k('DEU1'), k('PHY2'), k('PHY3')];

  it('alles vorhanden: keine Änderung', () => {
    expect(hatAenderung(pruefeKurse(['12/5::MAT3', '12/5::ENG1'], liste))).toBe(false);
  });

  it('eindeutiger Nachfolger wird vorgeschlagen', () => {
    const e = pruefeKurse(['12/5::MAT2', '12/5::ENG1'], liste);
    expect(e.ersetzt).toEqual({ '12/5::MAT2': '12/5::MAT3' });
    expect(e.fehlend).toEqual([]);
    expect(mitNachfolgern(['12/5::MAT2', '12/5::ENG1'], e).sort()).toEqual(['12/5::ENG1', '12/5::MAT3']);
  });

  it('mehrdeutig oder ganz fort wird gemeldet', () => {
    const e = pruefeKurse(['12/5::PHY1', '12/5::KUN1'], liste);
    expect(e.ersetzt).toEqual({});
    expect(e.fehlend).toEqual(['12/5::KUN1', '12/5::PHY1']);
  });

  it('unbekannte Klasse und ganze Klasse bleiben unberührt', () => {
    expect(hatAenderung(pruefeKurse(['7a::*', '9b::MA1'], liste))).toBe(false);
    expect(hatAenderung(pruefeKurse(['12/5::MAT1'], []))).toBe(false);
  });
});

describe('Noten', () => {
  const n = (fach: string, p: number, art: 'KLAUSUR' | 'MUENDLICH', system: 'PUNKTE' | 'NOTEN' = 'PUNKTE', datum = '2025-10-01'): Note => ({
    id: Math.random().toString(), system, fach, punkte: p, art, datum, notiz: ''
  });

  it('runden und Schulnote', () => {
    expect(runden(9.5)).toBe(10);
    expect(runden(9.49)).toBe(9);
    expect(runden(15.4)).toBe(15);
    expect(runden(-1)).toBe(0);
    expect(alsSchulnote(15)).toBeCloseTo(1, 5);
    expect(alsSchulnote(12)).toBeCloseTo(2, 5);
    expect(alsSchulnote(0)).toBeCloseTo(6, 5);
    expect(runden(6.4, 'NOTEN')).toBe(6);
    expect(runden(0.2, 'NOTEN')).toBe(1);
    expect(runden(2.5, 'NOTEN')).toBe(3);
  });

  it('Halbjahre', () => {
    expect(halbjahr('2025-09-03')).toBe('2025/26 · 1. HJ');
    expect(halbjahr('2026-01-20')).toBe('2025/26 · 1. HJ');
    expect(halbjahr('2026-03-03')).toBe('2025/26 · 2. HJ');
    expect(halbjahr('2026-07-10')).toBe('2025/26 · 2. HJ');
    expect(halbjahr('2026-08-20')).toBe('2026/27 · 1. HJ');
    expect(halbjahrDavor('2025/26 · 2. HJ')).toBe('2025/26 · 1. HJ');
    expect(halbjahrDavor('2025/26 · 1. HJ')).toBe('2024/25 · 2. HJ');
    expect(halbjahrDavor('kaputt')).toBeNull();
    expect(halbjahrGrenzen('2026-01-15')).toEqual(['2025-08-01', '2026-01-31']);
    expect(halbjahrGrenzen('2026-10-15')).toEqual(['2026-08-01', '2027-01-31']);
    expect(halbjahrGrenzen('2026-04-15')).toEqual(['2026-02-01', '2026-07-31']);
  });

  it('Schnitt gewichtet', () => {
    const s = fachSchnitt([n('Mathe', 12, 'KLAUSUR'), n('Mathe', 8, 'KLAUSUR'), n('Mathe', 14, 'MUENDLICH')])!;
    expect(s.klausur).toBeCloseTo(10, 5);
    expect(s.muendlich).toBeCloseTo(14, 5);
    expect(s.gesamt).toBeCloseTo(12, 5);
    expect(zeugnis(s)).toBe(12);
    const liste = [n('Mathe', 10, 'KLAUSUR'), n('Mathe', 14, 'MUENDLICH')];
    expect(fachSchnitt(liste, 30)!.gesamt).toBeCloseTo(12.8, 5);
    expect(fachSchnitt(liste, 100)!.gesamt).toBeCloseTo(10, 5);
  });

  it('nur eine Art zählt allein; Fächer zusammenfassen', () => {
    const s = fachSchnitt([n('Kunst', 13, 'MUENDLICH'), n('Kunst', 11, 'MUENDLICH')])!;
    expect(s.klausur).toBeNull();
    expect(s.gesamt).toBeCloseTo(12, 5);
    expect(fachSchnitt([])).toBeNull();
    const faecher = nachFach([n('Mathe', 10, 'KLAUSUR'), n('mathe ', 12, 'KLAUSUR'), n('Deutsch', 14, 'MUENDLICH')]);
    expect(faecher).toHaveLength(2);
    expect(faecher[0].fach).toBe('Deutsch');
    expect(faecher[1].anzahl).toBe(2);
    expect(gesamtSchnitt(faecher)).toBeCloseTo(12.5, 5);
    expect(gesamtSchnitt([])).toBeNull();
  });

  it('was brauche ich noch (Punkte)', () => {
    const liste = [n('M', 10, 'KLAUSUR'), n('M', 10, 'MUENDLICH')];
    expect(benoetigt(liste, 'KLAUSUR', 12)).toEqual({ art: 'UNMOEGLICH' });
    expect(benoetigt(liste, 'KLAUSUR', 11)).toEqual({ art: 'PUNKTE', punkte: 12 });
    expect(benoetigt(liste, 'KLAUSUR', 10)).toEqual({ art: 'PUNKTE', punkte: 8 });
    expect(benoetigt(liste, 'KLAUSUR', 7)).toEqual({ art: 'SICHER' });
    const nurK = [n('M', 8, 'KLAUSUR')];
    expect(benoetigt(nurK, 'KLAUSUR', 10)).toEqual({ art: 'PUNKTE', punkte: 11 });
    expect(benoetigt([], 'KLAUSUR', 12)).toEqual({ art: 'PUNKTE', punkte: 12 });
    const nurM = [n('M', 13, 'MUENDLICH')];
    expect(benoetigt(nurM, 'KLAUSUR', 13, 0)).toEqual({ art: 'SICHER' });
    expect(benoetigt(nurM, 'KLAUSUR', 14, 0)).toEqual({ art: 'UNMOEGLICH' });
  });

  it('System aus den Kursen', () => {
    expect(systemAusKursIds(['12/5::D1', '12/5::M2'])).toBe('PUNKTE');
    expect(systemAusKursIds(['11/3::E1'])).toBe('PUNKTE');
    expect(systemAusKursIds(['7a::*'])).toBe('NOTEN');
    expect(systemAusKursIds(['10b::*', '5c::Ku'])).toBe('NOTEN');
    expect(systemAusKursIds(['10b::*', '12/5::D1'])).toBe('PUNKTE');
    expect(systemAusKursIds([])).toBe('PUNKTE');
    expect(systemAusKursIds(['Q1::D1'])).toBe('PUNKTE');
  });

  it('Schnitt und was-brauche-ich bei Noten 1–6', () => {
    const s = fachSchnitt([n('Mathe', 2, 'KLAUSUR', 'NOTEN'), n('Mathe', 3, 'KLAUSUR', 'NOTEN'), n('Mathe', 2, 'MUENDLICH', 'NOTEN')], 50)!;
    expect(s.gesamt).toBeCloseTo(2.25, 5);
    expect(zeugnis(s)).toBe(2);
    const liste = [n('M', 3, 'KLAUSUR', 'NOTEN'), n('M', 3, 'MUENDLICH', 'NOTEN')];
    expect(benoetigt(liste, 'KLAUSUR', 3, 50, 'NOTEN')).toEqual({ art: 'PUNKTE', punkte: 4 });
    expect(benoetigt(liste, 'KLAUSUR', 4, 50, 'NOTEN')).toEqual({ art: 'SICHER' });
    expect(benoetigt(liste, 'KLAUSUR', 2, 50, 'NOTEN')).toEqual({ art: 'UNMOEGLICH' });
  });

  it('Prognose und Ziel', () => {
    const liste = [n('M', 10, 'KLAUSUR'), n('M', 10, 'MUENDLICH')];
    // Neue Klausur mit 14: Klausuren Ø 12, mündlich 10 → bei 50 % ergibt das 11
    const p = prognose('M', liste, 'PUNKTE', 'KLAUSUR', 14, 50)!;
    expect(p.gesamt).toBeCloseTo(11, 5);
    expect(zeugnis(p)).toBe(11);
    expect(prognose('M', [], 'PUNKTE', 'MUENDLICH', 13)!.gesamt).toBeCloseTo(13, 5);
    expect(zielErreicht(13, 12, 'PUNKTE')).toBe(true);
    expect(zielErreicht(11, 12, 'PUNKTE')).toBe(false);
    expect(zielErreicht(2, 2, 'NOTEN')).toBe(true);
    expect(zielErreicht(3, 2, 'NOTEN')).toBe(false);
  });

  it('Durchschnitt aller Halbjahre', () => {
    const a = n('M', 10, 'KLAUSUR', 'PUNKTE', '2025-10-01');
    const b = n('M', 14, 'KLAUSUR', 'PUNKTE', '2026-03-01');
    expect(durchschnittAllerHalbjahre([a, b])).toBeCloseTo(12, 5);
    expect(durchschnittAllerHalbjahre([])).toBeNull();
  });

  it('Abi-Rechner', () => {
    expect(Abi.blockI(15)).toBe(600);
    expect(Abi.blockI(10)).toBe(400);
    expect(Abi.blockI(-3)).toBe(0);
    expect(Abi.blockII([15, 15, 15, 15, 15])).toBe(300);
    expect(Abi.blockII([10, 10, 10, 10, 10])).toBe(200);
    expect(Abi.note(900)).toBeCloseTo(1, 5);
    expect(Abi.note(840)).toBeCloseTo(1, 5);
    expect(Abi.note(720)).toBeCloseTo(1.7, 5);
    expect(Abi.note(660)).toBeCloseTo(2, 5);
    expect(Abi.note(300)).toBeCloseTo(4, 5);
    expect(Abi.note(299)).toBeNull();
    expect(Abi.bestanden(400, 200)).toBe(true);
    expect(Abi.bestanden(199, 300)).toBe(false);
    expect(Abi.bestanden(500, 99)).toBe(false);
    expect(Abi.bestanden(250, 40)).toBe(false);
  });
});

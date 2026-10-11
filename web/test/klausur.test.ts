import { describe, expect, it } from 'vitest';
import { hinweisKurz } from '../src/logic/hinweis';
import { tagesplanFuer } from '../src/logic/models';
import { parseUhrzeit } from '../src/logic/zeit';
import { lesson, plan } from './helfer';

/**
 * Prüft die Klausur-/Ausfall-Erkennung an einem nachgebauten Plan (Jahrgang 12/5, Deutsch-Kurse
 * DEU1–DEU4) nach dem Muster des Originals: "Klausur!; BIO3 … fällt aus; DEU3 … fällt aus".
 */
describe('Klausur-Erkennung', () => {
  const liste =
    'Klausur!; BIO3 Herr Lonzer fällt aus; CHE1 Herr Gruß fällt aus; ' +
    'DEU3 Frau Meier fällt aus; DEU4 Herr Wesenberg fällt aus; MAT2 Frau Weisheit fällt aus';
  const alleDeutsch = new Set(['12/5::DEU1', '12/5::DEU2', '12/5::DEU3', '12/5::DEU4']);

  const std = (fach: string, info: string, ausfall: boolean) =>
    `<Std${ausfall ? ' Ausfall="1"' : ''}><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende>` +
    `<Fa>${fach}</Fa><Le>X</Le><Ra></Ra><Nr></Nr><If>${info}</If></Std>`;

  const planMit = (zusatz: string, info: string, ausfall: boolean) =>
    plan(`<?xml version="1.0" encoding="UTF-8"?>
<WplanVp><Kopf><zeitstempel>30.09.2026, 08:45</zeitstempel></Kopf>
${zusatz}
<Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU2</KKz></Ku><Ku><KKz>DEU3</KKz></Ku><Ku><KKz>DEU4</KKz></Ku><Ku><KKz>BIO3</KKz></Ku></Kurse>
<Pl>${['DEU1', 'DEU2', 'DEU3', 'DEU4'].map((k) => std(k, info, ausfall)).join('')}</Pl>
</Kl></Klassen></WplanVp>`);

  const zusatz = (text: string) => '<ZusatzInfo>' + text.split('; ').map((z) => `<ZiZeile>${z}</ZiZeile>`).join('') + '</ZusatzInfo>';

  it('Liste an jeder Stunde: eigener Kurs ist Klausur, genannte sind Ausfall', () => {
    const tp = tagesplanFuer(planMit('', liste, false), alleDeutsch);
    for (const k of ['DEU1', 'DEU2']) {
      expect(lesson(tp, k).istKlausur, `${k} soll Klausur sein`).toBe(true);
      expect(lesson(tp, k).entfaellt, `${k} darf nicht ausfallen`).toBe(false);
    }
    for (const k of ['DEU3', 'DEU4']) {
      expect(lesson(tp, k).entfaellt, `${k} soll Ausfall sein`).toBe(true);
      expect(lesson(tp, k).istKlausur, `${k} ist keine Klausur`).toBe(false);
    }
  });

  it('Liste als Tageshinweis: ausfallende Stunden werden Klausur', () => {
    const tp = tagesplanFuer(planMit(zusatz(liste), '', true), alleDeutsch);
    for (const k of ['DEU1', 'DEU2']) {
      expect(lesson(tp, k).istKlausur).toBe(true);
      expect(lesson(tp, k).entfaellt).toBe(false);
    }
    for (const k of ['DEU3', 'DEU4']) expect(lesson(tp, k).entfaellt).toBe(true);
  });

  it('nur allgemeines Klausur-Wort ändert nichts', () => {
    const tp = tagesplanFuer(planMit(zusatz('Klausur!'), '', true), alleDeutsch);
    expect(tp.stunden.every((l) => l.entfaellt && !l.istKlausur)).toBe(true);
  });

  it('ohne Klausur-Hinweis bleibt Ausfall Ausfall', () => {
    const tp = tagesplanFuer(planMit('', 'DEU1 Herr Got fällt aus', false), alleDeutsch);
    expect(lesson(tp, 'DEU1').entfaellt).toBe(true);
    expect(lesson(tp, 'DEU1').istKlausur).toBe(false);
  });

  it('nur eigener Kurs: zeigt nur eigene Stunden und Hinweise', () => {
    const tp = tagesplanFuer(planMit(zusatz(liste), '', true), new Set(['12/5::DEU1']));
    expect(tp.stunden.map((l) => l.fach)).toEqual(['DEU1']);
    // "Klausur!" ist allgemein und bleibt, Ausfälle fremder Kurse fliegen raus.
    expect(tp.hinweise).toEqual(['Klausur!']);
  });

  it('fremdes Fach mit Liste im Hinweis ist kein Ausfall', () => {
    const g = plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>INF1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku><Ku><KKz>BIO3</KKz></Ku></Kurse>
<Pl><Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>INF1</Fa><Le>X</Le><Ra>131</Ra><Nr></Nr><If>${liste}</If></Std></Pl>
</Kl></Klassen></WplanVp>`);
    const l = lesson(tagesplanFuer(g, new Set(['12/5::INF1'])), 'INF1');
    expect(l.entfaellt, 'INF1 darf nicht als Entfall erscheinen').toBe(false);
    expect(l.istKlausur, 'INF1 ist nicht als Klausur belegt').toBe(false);
    expect(l.status).toBe('NORMAL');
  });

  it('Klausur behält Vertretungs-Status', () => {
    const g = plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku></Kurse>
<Pl><Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>DEU1</Fa><Le LeAe="Got">Mei</Le><Ra>033</Ra><Nr></Nr><If>${liste}</If></Std></Pl>
</Kl></Klassen></WplanVp>`);
    const l = lesson(tagesplanFuer(g, new Set(['12/5::DEU1'])), 'DEU1');
    expect(l.istKlausur).toBe(true);
    expect(l.entfaellt).toBe(false);
    expect(l.status).toBe('VERTRETUNG');
  });

  it('Platzhalter werden nicht als alter Raum angezeigt', () => {
    const g = plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>PHY1</KKz></Ku></Kurse>
<Pl><Std><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>PHY1</Fa><Le>Luth</Le><Ra RaAe="RaGeaendert">235</Ra><Nr></Nr></Std></Pl>
</Kl></Klassen></WplanVp>`);
    const l = g.klassen[0].stunden[0];
    expect(l.originalRoom).toBeNull();
    expect(l.status).toBe('RAUMAENDERUNG');
  });

  /** Ein Sport-Kurs neben den Deutsch-Kursen – [fach], [info] und Ausfall-Flag frei wählbar. */
  const sportPlan = (fach: string, info: string, zus = '', ausfall = false) => {
    const g = plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf>${zus}<Klassen><Kl><Kurz>12/5</Kurz>
<Kurse><Ku><KKz>SPO1</KKz></Ku><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU3</KKz></Ku><Ku><KKz>BIO3</KKz></Ku></Kurse>
<Pl><Std${ausfall ? ' Ausfall="1"' : ''}><St>1</St><Beginn>07:15</Beginn><Ende>08:00</Ende><Fa>${fach}</Fa><Le>X</Le><Ra></Ra><Nr></Nr><If>${info}</If></Std></Pl>
</Kl></Klassen></WplanVp>`);
    const stunden = tagesplanFuer(g, new Set(['12/5::SPO1'])).stunden;
    expect(stunden).toHaveLength(1);
    return stunden[0];
  };

  it('Sport fällt aus wegen Klausur: Ausfall', () => {
    for (const info of ['SPO1 fällt aus wegen Klausur', 'SPO1 Herr X fällt aus (Klausuraufsicht)', 'Klausur DEU1; SPO1 fällt aus']) {
      const l = sportPlan('SPO1', info);
      expect(l.entfaellt, `'${info}': Sport muss ausfallen`).toBe(true);
      expect(l.istKlausur, `'${info}': Sport ist keine Klausur`).toBe(false);
    }
  });

  it('Sport mit Strichen und Klausur-Liste: Ausfall', () => {
    const l = sportPlan('---', liste);
    expect(l.entfaellt).toBe(true);
    expect(l.istKlausur).toBe(false);
  });

  it('Sport als Ausfall genannt am Klausurtag: Ausfall', () => {
    const l = sportPlan('SPO1', '', zusatz(liste + '; SPO1 Frau Y fällt aus'), true);
    expect(l.entfaellt).toBe(true);
    expect(l.istKlausur).toBe(false);
  });

  it('Klausur und Ausfall im selben Satz (mit Strich)', () => {
    const tp = tagesplanFuer(planMit(zusatz('Klausur DEU1, DEU2 – BIO3 Herr Lonzer fällt aus'), '', true), alleDeutsch);
    expect(lesson(tp, 'DEU1').istKlausur, 'DEU1 soll Klausur sein').toBe(true);
    expect(lesson(tp, 'DEU1').entfaellt, 'DEU1 findet statt').toBe(false);
  });
});

describe('Klausurliste des Plans', () => {
  const liste =
    'Klausur!; BIO4 Frau Dreier fällt aus; DEU2 Frau Gladow fällt aus; ' +
    'ENG1 Herr Niemietz fällt aus; ENG2 Frau Däumer fällt aus; ENG3 Herr Bretschneider fällt aus';

  const std = (nr: number, von: string, bis: string, fach: string, info: string) =>
    `<Std><St>${nr}</St><Beginn>${von}</Beginn><Ende>${bis}</Ende><Fa>${fach}</Fa><Le>Däu</Le><Ra>032</Ra><Nr></Nr><If>${info}</If></Std>`;

  const planMit = (klausuren: string) =>
    plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>ENG2</KKz></Ku><Ku><KKz>MAT2</KKz></Ku></Kurse><Pl>
${std(1, '07:15', '08:00', 'MAT2', '')}
${std(3, '09:05', '09:50', 'ENG2', liste)}
${std(4, '09:50', '10:35', 'ENG2', liste)}
${std(5, '11:00', '11:45', 'ENG2', liste)}
${std(6, '11:45', '12:30', 'ENG2', liste)}
${std(7, '12:55', '13:40', 'ENG2', '')}
</Pl></Kl></Klassen>${klausuren}</WplanVp>`);

  const eng2 = new Set(['12/5::ENG2', '12/5::MAT2']);

  it('Klausurliste macht Ausfall zur Klausur', () => {
    const g = planMit(
      '<Klausuren><Klausur><KlJahrgang>12</KlJahrgang><KlKurs>ENG2</KlKurs><KlBeginn>09:05</KlBeginn><KlDauer>180</KlDauer></Klausur></Klausuren>'
    );
    expect(g.klausuren).toEqual([{ jahrgang: '12', kurs: 'ENG2', beginn: parseUhrzeit('09:05'), dauerMinuten: 180 }]);
    const tp = tagesplanFuer(g, eng2);
    // 3.–6. Stunde liegen im Zeitraum 09:05–12:05: Klausur, nicht Ausfall.
    for (const nr of [3, 4, 5, 6]) {
      const l = tp.stunden.find((s) => s.stunde === nr)!;
      expect(l.istKlausur, `${nr}. Std ist Klausur`).toBe(true);
      expect(l.entfaellt, `${nr}. Std fällt nicht aus`).toBe(false);
    }
    // Die 7. Stunde liegt danach – nicht Teil der Klausur.
    expect(tp.stunden.find((s) => s.stunde === 7)!.istKlausur).toBe(false);
    // Der Hinweis zeigt keine "fällt aus"-Namen mehr.
    expect(hinweisKurz(tp.stunden.find((s) => s.stunde === 3)!)).toBe('Klausur!');
  });

  it('Klausur eines anderen Jahrgangs oder Kurses betrifft mich nicht', () => {
    const g = planMit(
      '<Klausuren><Klausur><KlJahrgang>11</KlJahrgang><KlKurs>ENG2</KlKurs><KlBeginn>09:05</KlBeginn><KlDauer>180</KlDauer></Klausur>' +
        '<Klausur><KlJahrgang>12</KlJahrgang><KlKurs>ENG3</KlKurs><KlBeginn>09:05</KlBeginn><KlDauer>180</KlDauer></Klausur></Klausuren>'
    );
    const l = tagesplanFuer(g, eng2).stunden.find((s) => s.stunde === 3)!;
    expect(l.istKlausur).toBe(false); // wie bisher: genannt = Ausfall
    expect(l.entfaellt).toBe(true);
  });

  it('Klausurliste in der Klasse nimmt den Jahrgang von dort', () => {
    const g = plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>ENG2</KKz></Ku></Kurse><Pl>
${std(3, '09:05', '09:50', 'ENG2', liste)}
</Pl><Klausuren><Klausur><Kurs>ENG2</Kurs><Beginn>9:05</Beginn><Dauer>90</Dauer></Klausur></Klausuren></Kl></Klassen></WplanVp>`);
    expect(g.klausuren).toHaveLength(1);
    expect(g.klausuren[0].jahrgang).toBe('12/5');
    expect(tagesplanFuer(g, new Set(['12/5::ENG2'])).stunden[0].istKlausur).toBe(true);
  });

  it('Kurs ohne Eintrag in der Liste bleibt Ausfall', () => {
    // Die Liste kennt nur ENG2; DEU1 wird nur aus dem Hinweistext als Klausur erraten.
    const deu = 'Klausur!; DEU2 Frau Gladow fällt aus; ENG2 Frau Däumer fällt aus';
    const g = plan(`<WplanVp><Kopf><zeitstempel>x</zeitstempel></Kopf><Klassen>
<Kl><Kurz>12/5</Kurz><Kurse><Ku><KKz>DEU1</KKz></Ku><Ku><KKz>DEU2</KKz></Ku></Kurse><Pl>
${std(3, '09:05', '09:50', 'DEU1', deu)}
${std(3, '09:05', '09:50', 'DEU2', deu)}
</Pl></Kl></Klassen><Klausuren><Klausur><KlJahrgang>12</KlJahrgang><KlKurs>ENG2</KlKurs>
<KlBeginn>09:05</KlBeginn><KlDauer>180</KlDauer></Klausur></Klausuren></WplanVp>`);
    const tp = tagesplanFuer(g, new Set(['12/5::DEU1', '12/5::DEU2']));
    expect(tp.stunden.every((l) => !l.istKlausur)).toBe(true);
  });
});

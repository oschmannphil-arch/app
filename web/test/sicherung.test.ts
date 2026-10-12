import { describe, expect, it } from 'vitest';
import { sicherungAlterText, sicherungFaellig } from '../src/services/sicherung';

const TAG = 24 * 3600 * 1000;
const jetzt = 100 * TAG;

describe('Sicherungs-Erinnerung', () => {
  it('erinnert nur, wenn es Daten gibt', () => {
    expect(sicherungFaellig(jetzt, { letzte: 0, spaeter: 0 }, false)).toBe(false);
    expect(sicherungFaellig(jetzt, { letzte: 0, spaeter: 0 }, true)).toBe(true);
  });
  it('erst nach 21 Tagen', () => {
    expect(sicherungFaellig(jetzt, { letzte: jetzt - 20 * TAG, spaeter: 0 }, true)).toBe(false);
    expect(sicherungFaellig(jetzt, { letzte: jetzt - 22 * TAG, spaeter: 0 }, true)).toBe(true);
  });
  it('"Später" blendet aus', () => {
    expect(sicherungFaellig(jetzt, { letzte: 0, spaeter: jetzt + TAG }, true)).toBe(false);
    expect(sicherungFaellig(jetzt, { letzte: 0, spaeter: jetzt - 1 }, true)).toBe(true);
  });
  it('Alterstext', () => {
    expect(sicherungAlterText(0, jetzt)).toBe('noch nie');
    expect(sicherungAlterText(jetzt - 1000, jetzt)).toBe('heute');
    expect(sicherungAlterText(jetzt - TAG, jetzt)).toBe('gestern');
    expect(sicherungAlterText(jetzt - 5 * TAG, jetzt)).toBe('vor 5 Tagen');
  });
});

import { alterText } from '../src/logic/zeit';

describe('Alter eines gespeicherten Stands', () => {
  const jetzt = 1_000_000_000_000;
  it('formuliert Minuten, Stunden und Tage', () => {
    expect(alterText(jetzt - 10_000, jetzt)).toBe('gerade eben');
    expect(alterText(jetzt - 5 * 60_000, jetzt)).toBe('vor 5 Min');
    expect(alterText(jetzt - 130 * 60_000, jetzt)).toBe('vor 2 Std 10 Min');
    expect(alterText(jetzt - 120 * 60_000, jetzt)).toBe('vor 2 Std');
    expect(alterText(jetzt - 24 * 3600_000, jetzt)).toBe('vor 1 Tag');
    expect(alterText(jetzt - 3 * 24 * 3600_000, jetzt)).toBe('vor 3 Tagen');
  });
});

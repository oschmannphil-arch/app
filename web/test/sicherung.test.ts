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

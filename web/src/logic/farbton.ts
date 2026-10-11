/** Farbton-Umrechnung für die Farbthemen (rein rechnerisch, damit testbar). */
export const Farbton = {
  /** Grundton der warmen (braunen) bzw. kalten (schieferblauen) Farben des Mocha-Designs. */
  WARM_BASIS: 28,
  KALT_BASIS: 197,

  /**
   * Setzt die Farbfamilie auf einen neuen Ton, behält aber die kleinen Unterschiede innerhalb der
   * Familie. Warm = Rot bis Gelb (bis 100°, ab 330°), alles andere kalt.
   */
  neu(farbton: number, warmZiel: number, kaltZiel: number): number {
    const warm = farbton <= 100 || farbton >= 330;
    const ergebnis = warm ? warmZiel + (((farbton - Farbton.WARM_BASIS + 540) % 360) - 180) : kaltZiel + (farbton - Farbton.KALT_BASIS);
    return ((ergebnis % 360) + 360) % 360;
  }
};

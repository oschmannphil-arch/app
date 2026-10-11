/**
 * Ein passendes Symbol (Emoji) zu einem Fach – erkannt am Namen oder am Kürzel aus dem Plan
 * ("Ma", "Mathematik", "Deutsch GK", "D1"). Unbekannte Fächer bekommen keins.
 */
const REGELN: Array<[string, string[], string[]]> = [
  ['🔢', ['m', 'ma', 'mat', 'mathe'], ['mathem']],
  ['📖', ['d', 'de', 'deu'], ['deutsch']],
  ['🇬🇧', ['e', 'en', 'eng', 'engl'], ['englisch']],
  ['🇫🇷', ['f', 'fr', 'fra', 'frz', 'franz'], ['franz']],
  ['🇪🇸', ['es', 'spa', 'span'], ['spanisch']],
  ['🇮🇹', ['it', 'ital'], ['italien']],
  ['🇷🇺', ['ru', 'rus'], ['russisch']],
  ['🏛️', ['l', 'la', 'lat'], ['latein']],
  ['⚛️', ['ph', 'phy'], ['physik']],
  ['🧪', ['ch', 'che', 'chem'], ['chemie']],
  ['🧬', ['bi', 'bio'], ['biolog']],
  ['📜', ['g', 'ge', 'gs', 'ges', 'gsch', 'gesch', 'geschi', 'gschi'], ['gesch']],
  ['🌍', ['ek', 'geo', 'erd'], ['geogr', 'erdkunde']],
  ['⚽', ['sp', 'spo', 'spt'], ['sport']],
  ['🎵', ['mu', 'mus'], ['musik']],
  ['🎨', ['ku', 'kun', 'kue', 'bk'], ['kunst']],
  ['💻', ['if', 'inf', 'info'], ['informatik']],
  ['✝️', ['re', 'rel', 'reli'], ['religion']],
  ['🤔', ['et', 'eth'], ['ethik']],
  ['🗳️', ['sk', 'pol', 'soz', 'sozi'], ['sozial', 'politik', 'gemeinschaftskunde']],
  ['💶', ['wi', 'wirt', 'wr'], ['wirtschaft']],
  ['🧠', ['psy', 'psych'], ['psycholog']],
  ['💭', ['phil'], ['philosoph']],
  ['🔭', ['astr'], ['astro']],
  ['🔧', ['wtk', 'tec'], ['technik', 'werken']],
  ['📝', ['sem'], ['seminar']]
];

/** Das Symbol für [fach] oder null, wenn keins passt. */
export function symbolFuer(fach: string): string | null {
  const wort = /[a-zäöüß]+/.exec(fach.toLowerCase())?.[0];
  if (!wort) return null;
  return REGELN.find(([, kuerzel, anfaenge]) => kuerzel.includes(wort) || anfaenge.some((a) => wort.startsWith(a)))?.[0] ?? null;
}

/** "🔢 Mathe" – oder nur der Name, wenn es kein Symbol gibt. */
export function mitSymbol(fach: string): string {
  const s = symbolFuer(fach);
  return s ? `${s} ${fach}` : fach;
}

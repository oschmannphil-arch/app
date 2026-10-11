import { AUSFALL_PHRASEN, nenntAusfall } from './hinweis';
import { type Zeitfenster, zeitraster as rechneZeitraster } from './raster';
import { KURS_MUSTER, SATZ_GRENZE, distinctBy, gleichOhneGross, hatBuchstabeOderZiffer, jahrgang, nurStriche, woerter } from './text';
import { type Min, TAG_MINUTEN } from './zeit';

void AUSFALL_PHRASEN;

export type LessonStatus = 'NORMAL' | 'ENTFALL' | 'VERTRETUNG' | 'RAUMAENDERUNG';

/** Eine einzelne Unterrichtsstunde (<Std> innerhalb von <Pl>). */
export interface Lesson {
  stunde: number;
  beginn: Min | null;
  ende: Min | null;
  fach: string;
  fachGeaendert: boolean;
  raum: string;
  raumGeaendert: boolean;
  originalRoom: string | null;
  lehrer: string;
  lehrerGeaendert: boolean;
  originalTeacher: string | null;
  info: string;
  entfaellt: boolean;
  hatAufgaben: boolean;
  status: LessonStatus;
  /** Unterrichtsnummer aus <Nr> – Schlüssel in die <Unterricht>-Tabelle. */
  unterrichtsNr: string | null;
  /** Kurskürzel, aufgelöst über <Nr> → <UeNr UeGr="…">. Null = gemeinsamer Klassenunterricht. */
  kursKuerzel: string | null;
  /** Klasse/Jahrgang, aus dessen Block diese Stunde stammt. */
  klasse: string;
  /** Der Plan weist diese Stunde als Klausur/Klassenarbeit aus. */
  istKlausur: boolean;
}

/** Eine Stunde mit Standardwerten – für Tests und für Teilangaben. */
export function neueStunde(teil: Partial<Lesson> & { stunde: number }): Lesson {
  return {
    beginn: null,
    ende: null,
    fach: '',
    fachGeaendert: false,
    raum: '',
    raumGeaendert: false,
    originalRoom: null,
    lehrer: '',
    lehrerGeaendert: false,
    originalTeacher: null,
    info: '',
    entfaellt: false,
    hatAufgaben: false,
    status: 'NORMAL',
    unterrichtsNr: null,
    kursKuerzel: null,
    klasse: '',
    istKlausur: false,
    ...teil
  };
}

export function hatAenderung(l: Lesson): boolean {
  return l.status !== 'NORMAL' || l.fachGeaendert || l.info.trim() !== '';
}

/** Pseudo-Kürzel für "die ganze Klasse", damit auch Klassen ohne Kurssystem wählbar sind. */
export const GANZE_KLASSE = '*';

/**
 * Ein wählbarer Kurs. [kursId] ist eindeutig über die ganze Schule hinweg, weil dasselbe
 * Kürzel in mehreren Jahrgängen vorkommen kann.
 */
export interface KursInfo {
  klasse: string;
  kuerzel: string;
  fach: string;
  lehrer: string;
}

export const kursId = (k: KursInfo) => `${k.klasse}::${k.kuerzel}`;
export const istGanzeKlasse = (k: KursInfo) => k.kuerzel === GANZE_KLASSE;

/** Anzeigetext, z.B. "D1 · De (Müller)". */
export function kursAnzeige(k: KursInfo): string {
  if (istGanzeKlasse(k)) return 'Ganze Klasse – alle Stunden';
  let s = k.kuerzel;
  if (k.fach.trim() !== '' && !gleichOhneGross(k.fach, k.kuerzel)) s += ` · ${k.fach}`;
  if (k.lehrer.trim() !== '') s += ` (${k.lehrer})`;
  return s;
}

/** Text, gegen den die Suche in der Kursauswahl läuft. */
export const kursSuchtext = (k: KursInfo) => `${k.klasse} ${k.kuerzel} ${k.fach} ${k.lehrer}`.toLowerCase();

/** Kopfdaten eines Plans. */
export interface PlanKopf {
  datumPlan: string;
  zeitstempel: string;
  schulnummer: string;
  /** Tageshinweise der Schule (<ZusatzInfo>/<ZiZeile>), z.B. "Klausur!". */
  zusatzInfo: string[];
}

/** Der Block einer einzelnen Klasse/eines Jahrgangs aus der XML. */
export interface KlassenPlan {
  klasse: string;
  stunden: Lesson[];
  kurse: KursInfo[];
}

/**
 * Eine Klausur laut Klausurliste des Plans ("Klausur 09:05: ENG2, Jg. 12 (180 min.)"). Das ist
 * die verlässlichste Quelle: Stunden dieses Kurses im Zeitraum sind Klausur – auch wenn der Plan
 * sie zugleich als "fällt aus" aufführt, weil der normale Unterricht der Klausur weicht.
 */
export interface KlausurTermin {
  jahrgang: string;
  kurs: string;
  beginn: Min;
  dauerMinuten: number;
}

/** Ende der Klausur; wäre es nach Mitternacht, gilt Mitternacht. */
export function klausurEnde(t: KlausurTermin): Min {
  return Math.min(t.beginn + t.dauerMinuten, TAG_MINUTEN);
}

/** Gehört [l] zu dieser Klausur: gleicher Kurs und Jahrgang, und die Stunde liegt im Zeitraum. */
export function klausurBetrifft(t: KlausurTermin, l: Lesson): boolean {
  const kurs = (l.kursKuerzel ?? '').trim();
  if (!gleichOhneGross(kurs, t.kurs.trim())) return false;
  if (
    t.jahrgang.trim() !== '' &&
    !gleichOhneGross(jahrgang(l.klasse), t.jahrgang.trim()) &&
    !gleichOhneGross(l.klasse, t.jahrgang.trim())
  ) {
    return false;
  }
  if (l.beginn === null || l.ende === null) return false;
  return l.beginn < klausurEnde(t) && l.ende > t.beginn;
}

/** Alles, was in einer PlanKl-Datei steht – alle Klassen der Schule für diesen Tag. */
export interface GesamtPlan {
  kopf: PlanKopf;
  klassen: KlassenPlan[];
  klausuren: KlausurTermin[];
}

const rasterCache = new WeakMap<GesamtPlan, Zeitfenster[]>();

/** Zeitraster der ganzen Schule an diesem Tag – daraus ergeben sich die Freistunden. */
export function zeitrasterVon(g: GesamtPlan): Zeitfenster[] {
  let r = rasterCache.get(g);
  if (!r) {
    r = rechneZeitraster(g.klassen.flatMap((k) => k.stunden));
    rasterCache.set(g, r);
  }
  return r;
}

/** Alle Kurse der ganzen Schule, für die Auswahlliste ("ganze Klasse" jeweils zuerst). */
export function alleKurse(g: GesamtPlan): KursInfo[] {
  return distinctBy(
    g.klassen.flatMap((k) => k.kurse),
    kursId
  ).sort((a, b) => {
    if (a.klasse !== b.klasse) return a.klasse < b.klasse ? -1 : 1;
    const ga = istGanzeKlasse(a) ? 0 : 1;
    const gb = istGanzeKlasse(b) ? 0 : 1;
    if (ga !== gb) return ga - gb;
    return a.kuerzel < b.kuerzel ? -1 : a.kuerzel > b.kuerzel ? 1 : 0;
  });
}

/**
 * Die Stunde findet statt (war nur wegen fremder Ausfall-Hinweise als Entfall markiert).
 * Vertretung/Raumänderung bleiben dabei erhalten, statt pauschal auf NORMAL zu fallen.
 */
function findetStatt(l: Lesson, klausur: boolean): Lesson {
  return {
    ...l,
    entfaellt: false,
    istKlausur: klausur,
    status: l.lehrerGeaendert ? 'VERTRETUNG' : l.raumGeaendert ? 'RAUMAENDERUNG' : 'NORMAL'
  };
}

/** Der fertig gefilterte, persönliche Plan für einen Tag. */
export interface TagesPlan {
  kopf: PlanKopf;
  klasse: string;
  stunden: Lesson[];
  /** Tageshinweise der Schule, bereits auf die eigenen Kurse gefiltert. */
  hinweise: string[];
}

/**
 * Baut aus den gewählten Kurs-IDs den persönlichen Tagesplan.
 *
 * Die Klasse muss der Schüler nicht angeben: Es zählen die Klassen-Blöcke, in denen
 * mindestens einer seiner Kurse liegt. Aus diesen Blöcken werden die Stunden seiner
 * Kurse übernommen – plus die Stunden ohne Kurskürzel (gemeinsamer Klassenunterricht).
 */
export function tagesplanFuer(g: GesamtPlan, gewaehlteKursIds: ReadonlySet<string>): TagesPlan {
  const ganzeKlasseGewaehlt = (klasse: string) => gewaehlteKursIds.has(`${klasse}::${GANZE_KLASSE}`);

  /**
   * Kurs einer Stunde. Fehlt die Zuordnung über <Nr> (z.B. bei ausfallenden oder geänderten
   * Stunden), steht der Kurs oft im Fach ("DEU1") – dann darüber zuordnen. Ohne diesen Rückgriff
   * gälte die Stunde als "Klassenunterricht" und würde für ALLE Kurse angezeigt.
   */
  const kuerzelProKlasse = new Map<string, string[]>();
  const kursVon = (kp: KlassenPlan, l: Lesson): string | null => {
    if (l.kursKuerzel && l.kursKuerzel.trim() !== '') return l.kursKuerzel;
    const fach = l.fach.trim();
    if (fach === '') return null;
    let kuerzel = kuerzelProKlasse.get(kp.klasse);
    if (!kuerzel) {
      kuerzel = kp.kurse.filter((k) => !istGanzeKlasse(k)).map((k) => k.kuerzel);
      kuerzelProKlasse.set(kp.klasse, kuerzel);
    }
    const genau = kuerzel.find((k) => k === fach);
    if (genau !== undefined) return genau;
    const passend = kuerzel.filter((k) => gleichOhneGross(k, fach));
    return passend.length === 1 ? passend[0] : null;
  };

  // Nur ganze Klassen gewählt (Suche nach Lehrern/Räumen): die Stunden der anderen Klassen
  // müssen nicht durchsucht werden – sonst wäre es Klassen × alle Stunden.
  const nurGanzeKlassen = [...gewaehlteKursIds].every((id) => id.endsWith(`::${GANZE_KLASSE}`));
  const betroffene = g.klassen.filter(
    (kp) =>
      ganzeKlasseGewaehlt(kp.klasse) ||
      (!nurGanzeKlassen &&
        (kp.kurse.some((k) => gewaehlteKursIds.has(kursId(k))) ||
          kp.stunden.some((l) => {
            const kurs = kursVon(kp, l);
            return kurs !== null && gewaehlteKursIds.has(`${kp.klasse}::${kurs}`);
          })))
  );

  const rohStunden: Lesson[] = betroffene
    .flatMap((kp) => {
      const alles = ganzeKlasseGewaehlt(kp.klasse);
      const out: Lesson[] = [];
      for (const l of kp.stunden) {
        const kurs = kursVon(kp, l);
        if (alles || kurs === null || kurs.trim() === '' || gewaehlteKursIds.has(`${kp.klasse}::${kurs}`)) {
          out.push(kurs !== l.kursKuerzel ? { ...l, kursKuerzel: kurs } : l);
        }
      }
      return out;
    })
    .sort((a, b) => a.stunde - b.stunde);

  // Tageshinweise der Schule ("Klausur!; BIO3 Herr X fällt aus; …"): Zeilen, die einen
  // fremden Kurs nennen, fliegen raus – allgemeine Zeilen und die eigenen bleiben.
  const alleKuerzel = new Set(
    g.klassen
      .flatMap((k) => k.kurse)
      .filter((k) => !istGanzeKlasse(k))
      .map((k) => k.kuerzel)
      .filter((k) => k.trim() !== '')
  );
  const eigeneKuerzel = new Set(
    [...gewaehlteKursIds].map((id) => id.slice(id.indexOf('::') + 2)).filter((k) => k !== GANZE_KLASSE)
  );
  const alleGewaehlt = betroffene.some((k) => ganzeKlasseGewaehlt(k.klasse));
  const nennt = (zeile: string, kuerzel: ReadonlySet<string>) => woerter(zeile).some((w) => kuerzel.has(w));
  // Fremd ist eine Zeile auch, wenn sie ein Kurskürzel nennt, das in keiner Kursliste dieses Plans steht.
  const eigeneKlassen = new Set(betroffene.map((k) => k.klasse));
  const hinweise = g.kopf.zusatzInfo.filter(
    (zeile) =>
      alleGewaehlt ||
      nennt(zeile, eigeneKuerzel) ||
      nennt(zeile, eigeneKlassen) ||
      (!nennt(zeile, alleKuerzel) && !KURS_MUSTER.test(zeile))
  );

  // Klausurtage: Die Schule schreibt "Klausur!" und listet die ausfallenden Kurse einzeln auf
  // ("BIO3 Herr X fällt aus"). Die Kurse, die im Plan als ausgefallen erscheinen, aber NICHT als
  // Ausfall genannt werden, schreiben die Klausur. Das gilt nur für Kurse desselben Fachs wie die
  // genannten (DEU1 neben DEU3/DEU4), damit ein anderes, wirklich ausgefallenes Fach nicht zur
  // Klausur wird. Quellen: Tageshinweise der Schule UND die Info-Texte der eigenen Stunden.
  const segmente = distinctBy(
    [...g.kopf.zusatzInfo, ...rohStunden.map((s) => s.info)]
      .flatMap((t) => t.split(SATZ_GRENZE))
      .map((t) => t.trim())
      .filter((t) => t !== ''),
    (t) => t
  );
  const istAusfallText = (s: string) => nenntAusfall(s);
  // Ein Satz mit "fällt aus" meldet einen Ausfall – auch wenn er die Klausur als Grund nennt.
  const klausurSegmente = segmente.filter((s) => {
    const k = s.toLowerCase();
    return (k.includes('klausur') || k.includes('klassenarbeit')) && !istAusfallText(s);
  });
  const ausfallSegmente = segmente.filter(istAusfallText);
  const praefix = (kuerzel: string) => {
    let i = 0;
    while (i < kuerzel.length && /\p{L}/u.test(kuerzel[i])) i++;
    return kuerzel.slice(0, i).toLowerCase();
  };
  const klausurWoerter = new Set(klausurSegmente.flatMap((s) => woerter(s)));
  const ausfallWoerter = new Set(ausfallSegmente.flatMap((s) => woerter(s)));
  const genannteFaecher =
    klausurSegmente.length === 0
      ? new Set<string>()
      : new Set([...alleKuerzel].filter((k) => klausurWoerter.has(k) || ausfallWoerter.has(k)).map(praefix));

  const bewertet: Lesson[] =
    klausurSegmente.length === 0
      ? rohStunden
      : rohStunden.map((l) => {
          const kurs = l.kursKuerzel;
          if (kurs === null || !(l.entfaellt || l.istKlausur)) return l;
          // Sagt die Stunde selbst "… fällt aus", ohne einen anderen Kurs zu nennen, bleibt es Ausfall.
          const eigenerAusfall = l.info
            .split(';')
            .map((t) => t.trim())
            .some((t) => istAusfallText(t) && !nennt(t, alleKuerzel) && !KURS_MUSTER.test(t));
          if (eigenerAusfall) return l;
          const ausdruecklich = klausurWoerter.has(kurs);
          const alsAusfallGenannt = ausfallWoerter.has(kurs);
          if (ausdruecklich || (!alsAusfallGenannt && genannteFaecher.has(praefix(kurs)))) {
            return findetStatt(l, true);
          }
          // Der Kurs wird ausdrücklich als Ausfall genannt: das ist keine Klausur.
          if (l.istKlausur && l.entfaellt && alsAusfallGenannt) return { ...l, istKlausur: false };
          // Die Stunde trägt die Klausur-Liste nur im eigenen Hinweis und fällt allein wegen der
          // Wendungen über ANDERE Kurse aus – sie selbst wird nicht genannt. Normal anzeigen.
          if (l.istKlausur && l.entfaellt && !nurStriche(l.fach) && l.lehrer !== '---') return findetStatt(l, false);
          return l;
        });
  // Was ausfällt, ist keine Klausur – egal, ob "Klausur" irgendwo im Hinweis stand.
  const vermutet = bewertet.map((l) => (l.entfaellt && l.istKlausur ? { ...l, istKlausur: false } : l));

  // Die Klausurliste des Plans geht vor: Stunden eines Kurses im Klausurzeitraum sind Klausur,
  // auch wenn der Plan denselben Kurs zugleich unter "fällt aus" nennt.
  const stunden =
    g.klausuren.length === 0
      ? vermutet
      : vermutet.map((l, i) => {
          if (g.klausuren.some((t) => klausurBetrifft(t, l))) {
            return l.entfaellt || !l.istKlausur ? findetStatt(l, true) : l;
          }
          // Die Liste kennt diesen Kurs nicht: Eine nur aus dem Hinweistext erratene Klausur
          // zurücknehmen – der Plan selbst hatte die Stunde als Ausfall geführt.
          if (l.istKlausur && !l.entfaellt && rohStunden[i].entfaellt) return { ...rohStunden[i], istKlausur: false };
          return l;
        });

  return {
    kopf: g.kopf,
    klasse: betroffene.map((k) => k.klasse).join(' / '),
    stunden,
    hinweise
  };
}

/**
 * Ergebnis von [naechsteStunde]: die Stunde plus Info, ob es sich schon um eine Vorschau auf die
 * kommende Stunde handelt (weil die laufende gleich endet).
 */
export interface NaechsteStundeErgebnis {
  lesson: Lesson;
  istVorschau: boolean;
}

/** So viele Minuten vor Stundenende wird schon die nächste Stunde gezeigt. */
export const VORLAUF_MINUTEN = 5;

/**
 * Liefert die laufende oder als nächstes anstehende Stunde relativ zu [jetzt]. Endet die laufende
 * Stunde in [vorlaufMinuten] oder weniger, wird schon die darauffolgende zurückgegeben.
 */
export function naechsteStunde(plan: TagesPlan, jetzt: Min, vorlaufMinuten = VORLAUF_MINUTEN): NaechsteStundeErgebnis | null {
  const sortiert = plan.stunden.filter((l) => !l.entfaellt).sort((a, b) => a.stunde - b.stunde);

  const laufende = sortiert.find((l) => l.beginn !== null && l.ende !== null && jetzt >= l.beginn && jetzt < l.ende);

  if (laufende) {
    const rest = laufende.ende !== null ? laufende.ende - jetzt : Number.MAX_SAFE_INTEGER;
    if (rest > vorlaufMinuten) return { lesson: laufende, istVorschau: false };
    const danach = sortiert.find((l) => l.stunde > laufende.stunde && l.beginn !== null);
    return { lesson: danach ?? laufende, istVorschau: danach !== undefined };
  }

  const kommende = sortiert.find((l) => l.beginn !== null && jetzt < l.beginn);
  return kommende ? { lesson: kommende, istVorschau: false } : null;
}

/** Erste nicht ausgefallene Stunde des Tages – für die Vorschau auf den nächsten Schultag. */
export function ersteStunde(plan: TagesPlan): Lesson | null {
  const aktiv = plan.stunden.filter((l) => !l.entfaellt);
  if (aktiv.length === 0) return null;
  return aktiv.reduce((a, b) => (b.stunde < a.stunde ? b : a));
}

/** Findet an diesem Tag Unterricht im selben Kurs wie [vorbild] statt? */
export function hatStundeVon(plan: TagesPlan, vorbild: Lesson): boolean {
  return plan.stunden.some((l) => {
    if (l.entfaellt) return false;
    if (vorbild.kursKuerzel !== null) return l.kursKuerzel === vorbild.kursKuerzel && l.klasse === vorbild.klasse;
    return hatBuchstabeOderZiffer(vorbild.fach) && l.fach === vorbild.fach;
  });
}

/** Ende der letzten stattfindenden Stunde – "Schulschluss". */
export function schluss(plan: TagesPlan): Min | null {
  const enden = plan.stunden.filter((l) => !l.entfaellt).map((l) => l.ende).filter((e): e is Min => e !== null);
  return enden.length === 0 ? null : Math.max(...enden);
}

/**
 * Freistunden: Lücken von mindestens [minMinuten] zwischen zwei Stunden (normale Pausen sind
 * kürzer). Ausgefallene Stunden zählen als belegt. Ergebnis: Index der Stunde, VOR der die Lücke
 * liegt, mit Beginn und Ende der Lücke.
 */
export function freistunden(plan: TagesPlan, minMinuten = 30): Array<[number, Min, Min]> {
  const out: Array<[number, Min, Min]> = [];
  let bisherEnde: Min | null = null;
  plan.stunden.forEach((l, i) => {
    const vorher = bisherEnde;
    if (l.beginn !== null && vorher !== null && l.beginn - vorher >= minMinuten) {
      out.push([i, vorher, l.beginn]);
    }
    if (l.ende !== null && (vorher === null || l.ende > vorher)) bisherEnde = l.ende;
  });
  return out;
}

/** Zugangsdaten, wie sie lokal auf dem Gerät gespeichert werden. */
export interface Zugang {
  schulnummer: string;
  benutzername: string;
  passwort: string;
}

export const zugangVollstaendig = (z: Zugang) =>
  z.schulnummer.trim() !== '' && z.benutzername.trim() !== '' && z.passwort.trim() !== '';

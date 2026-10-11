import { AUSFALL_PHRASEN } from './hinweis';
import {
  type GesamtPlan,
  GANZE_KLASSE,
  type KlassenPlan,
  type KlausurTermin,
  type KursInfo,
  type Lesson,
  type LessonStatus
} from './models';
import { distinctBy, hatBuchstabeOderZiffer, nurStriche, textVergleich } from './text';
import { parseUhrzeit } from './zeit';

/**
 * Parser für die Indiware-mobil-XML-Dateien (mobdaten/PlanKl{JJJJMMTT}.xml).
 *
 *   <Kopf><zeitstempel>…</zeitstempel><DatumPlan>…</DatumPlan><schulnummer>…</schulnummer></Kopf>
 *   <Klassen>
 *     <Kl>                                        (manche Versionen: <Klasse>)
 *       <Kurz>JG12</Kurz>
 *       <Kurse><Ku><KKz KLe="Müller">D1</KKz></Ku></Kurse>              ← wählbare Kurse
 *       <Unterricht><Ue><UeNr UeLe="Müller" UeFa="De" UeGr="D1">101</UeNr></Ue></Unterricht>
 *       <Pl><Std><St>1</St><Beginn>07:45</Beginn><Ende>08:30</Ende>
 *              <Fa FaAe="…">De</Fa><Le>Müller</Le><Ra RaAe="…">204</Ra>
 *              <Nr>101</Nr><If>…</If></Std></Pl>
 *     </Kl>
 *   </Klassen>
 *
 * Zwei Fallstricke: <Pl> ist der Container, nicht die Stunde. Und das Kurskürzel steht nicht in der
 * Stunde – nur <Nr>; aufgelöst wird über <Unterricht>. Es werden immer ALLE Klassen geparst, damit
 * der Schüler nur seine Kurse auswählen muss und die Klasse daraus abgeleitet werden kann.
 */

const KLASSE_TAGS = new Set(['Kl', 'Klasse']);

// Stichwörter am Wortanfang ("selbst…", "aufgab…", "moodle" …) greifen auch bei Zusammensetzungen
// wie "Selbstbeschäftigung". "eva" und "frei" nur als ganzes Wort – sonst träfen sie "Freitag",
// "Evangelisch" oder Namen wie "Evers". Ohne Lookbehind (siehe text.ts).
const ANFANG = '(?:^|[^\\p{L}\\p{N}])';
const STICHWOERTER_AM_WORTANFANG =
  ANFANG + '(?:selbst|eigenv|aufgab|moodle|online|distanz|zuhause|homeoffice|stillarbeit|freiarbeit|lernzeit)';
const ENTFALL_WOERTER = new RegExp(STICHWOERTER_AM_WORTANFANG + '|' + ANFANG + '(?:eva|frei)(?![\\p{L}\\p{N}])', 'u');
/** Fürs Lehrer-Feld nur die langen Stichwörter: "Frei" oder "Eva" sind dort Namen. */
const ENTFALL_WOERTER_LEHRER = new RegExp(STICHWOERTER_AM_WORTANFANG, 'u');
/** Platzhalter statt echter Namen ("LeAe", "LeÄnderung", "LeGeaendert") – kein Lehrer. */
const LEHRER_PLATZHALTER = /^le\s*(ae|änd\w*)$/i;
/** "kein Unterricht", "keinen Unterricht" – nicht "Vertretungsunterricht, keine Pause". */
const KEIN_UNTERRICHT = /kein(e|en)?\s+unterricht/;
/** "fällt aus", "fällt heute aus" – aber nicht "fällt … Hausaufgaben". */
const FAELLT_AUS = /f(?:ä|ae)llt(?![\p{L}\p{N}]).{0,30}?[^\p{L}\p{N}]aus(?![\p{L}\p{N}])/u;
/** Hinweise auf eine Klausur im Infotext der Stunde. */
const KLAUSUR_WOERTER = /klausur|klassenarbeit/;
/** Eindeutige Wendungen – hier reicht ein Teilstring. */
const ENTFALL_PHRASEN = [...AUSFALL_PHRASEN, 'ausfall', 'absage', 'abgesagt', 'zu hause'];

function istPlatzhalter(text: string): boolean {
  const t = text.trim().toLowerCase();
  return t.includes('geaendert') || t.includes('geändert') || LEHRER_PLATZHALTER.test(text.trim());
}

// ---------- DOM-Helfer ----------

function kinder(el: Element | null | undefined): Element[] {
  return el ? Array.from(el.children) : [];
}

/** Erstes direktes Kind-Element mit diesem Tag-Namen (ohne Groß-/Kleinschreibung). */
function kind(el: Element | null | undefined, name: string): Element | null {
  const n = name.toLowerCase();
  return kinder(el).find((k) => k.tagName.toLowerCase() === n) ?? null;
}

/** Alle Nachfahren mit diesem Tag-Namen (ohne Groß-/Kleinschreibung, beliebige Tiefe). */
function nachfahren(el: Element | null | undefined, name: string): Element[] {
  if (!el) return [];
  const n = name.toLowerCase();
  return Array.from(el.getElementsByTagName('*')).filter((k) => k.tagName.toLowerCase() === n);
}

function text(el: Element | null | undefined): string {
  return el?.textContent?.trim() ?? '';
}

function attr(el: Element | null | undefined, name: string): string {
  return el?.getAttribute(name) ?? '';
}

function hatAttribut(el: Element | null | undefined, name: string): boolean {
  return el != null && attr(el, name).trim() !== '';
}

function toIntOrNull(s: string): number | null {
  return /^[+-]?\d+$/.test(s.trim()) ? parseInt(s, 10) : null;
}

/** Wandelt den Text einer PlanKl-Datei in einen Gesamtplan; null, wenn es kein gültiger Plan ist. */
export function parsePlan(xml: string, schulnummerFallback: string): GesamtPlan | null {
  let doc: Document;
  try {
    doc = new DOMParser().parseFromString(xml, 'text/xml');
  } catch {
    return null;
  }
  // Ungültiges XML (z.B. eine HTML-Fehlerseite) ergibt je nach Browser ein <parsererror>.
  if (doc.getElementsByTagName('parsererror').length > 0) return null;
  const root = doc.documentElement;
  if (!root) return null;

  const kopfEl = kind(root, 'Kopf');
  const zeilen = nachfahren(root, 'ZiZeile');
  const zusatz = (zeilen.length > 0 ? zeilen : kinder(kind(root, 'ZusatzInfo')))
    .map(text)
    .filter((t) => t.trim() !== '');
  const kopf = {
    datumPlan: text(kind(kopfEl, 'DatumPlan')),
    zeitstempel: text(kind(kopfEl, 'zeitstempel')),
    schulnummer: text(kind(kopfEl, 'schulnummer')) || schulnummerFallback,
    zusatzInfo: zusatz
  };

  const klassenEl = kind(root, 'Klassen');
  let kandidaten = kinder(klassenEl).filter((k) => KLASSE_TAGS.has(k.tagName));
  if (kandidaten.length === 0) kandidaten = kinder(klassenEl);
  const klassen = kandidaten.map(klasseElement).filter((k): k is KlassenPlan => k !== null);

  if (kopf.zeitstempel.trim() === '' && klassen.length === 0) return null;

  return { kopf, klassen, klausuren: klausurTermine(root) };
}

/**
 * Die Klausurliste: <Klausur> mit Kurs, Jahrgang, Beginn und Dauer (Minuten). Die Feldnamen tragen
 * je nach Schule ein Präfix ("KlKurs", "KlBeginn" …), darum zählt nur das Ende des Namens. Fehlt der
 * Jahrgang, kommt er aus der umgebenden Klasse.
 */
function klausurTermine(root: Element): KlausurTermin[] {
  const out: KlausurTermin[] = [];
  for (const el of nachfahren(root, 'Klausur')) {
    const feld = (ende: string) => text(kinder(el).find((k) => k.tagName.toLowerCase().endsWith(ende.toLowerCase())));
    const kurs = feld('Kurs');
    const beginn = parseUhrzeit(feld('Beginn'));
    const dauer = toIntOrNull(feld('Dauer'));
    if (kurs.trim() === '' || beginn === null || dauer === null || dauer <= 0) continue;

    let jg = feld('Jahrgang');
    if (jg.trim() === '') {
      let eltern: Node | null = el.parentNode;
      while (eltern && !(eltern.nodeType === 1 && KLASSE_TAGS.has((eltern as Element).tagName))) eltern = eltern.parentNode;
      jg = text(kind(eltern as Element | null, 'Kurz'));
    }
    out.push({ jahrgang: jg, kurs, beginn, dauerMinuten: dauer });
  }
  return distinctBy(out, (t) => `${t.jahrgang}|${t.kurs}|${t.beginn}|${t.dauerMinuten}`);
}

function klasseElement(kl: Element): KlassenPlan | null {
  const name = text(kind(kl, 'Kurz'));
  if (name.trim() === '') return null;

  // <Unterricht>: Nr → Kursgruppe, plus Fach/Lehrer je Gruppe
  const nrZuKurs = new Map<string, string>();
  const kursZuFach = new Map<string, string>();
  const kursZuLehrer = new Map<string, string>();
  for (const ueNr of nachfahren(kind(kl, 'Unterricht'), 'UeNr')) {
    const nr = text(ueNr);
    const gruppe = attr(ueNr, 'UeGr').trim();
    const fach = attr(ueNr, 'UeFa').trim();
    const lehrer = attr(ueNr, 'UeLe').trim();
    if (nr.trim() !== '' && gruppe !== '') {
      nrZuKurs.set(nr, gruppe);
      if (fach !== '' && !kursZuFach.has(gruppe)) kursZuFach.set(gruppe, fach);
      if (lehrer !== '' && !kursZuLehrer.has(gruppe)) kursZuLehrer.set(gruppe, lehrer);
    }
  }

  // <Kurse>: wählbare Kurse
  const kurse = new Map<string, KursInfo>();
  for (const kkz of nachfahren(kind(kl, 'Kurse'), 'KKz')) {
    const kuerzel = text(kkz);
    if (kuerzel.trim() === '') continue;
    const le = attr(kkz, 'KLe').trim();
    kurse.set(kuerzel, {
      klasse: name,
      kuerzel,
      fach: kursZuFach.get(kuerzel) ?? '',
      lehrer: le !== '' ? le : (kursZuLehrer.get(kuerzel) ?? '')
    });
  }
  // Kurse, die nur in <Unterricht> vorkommen, ebenfalls anbieten
  for (const gruppe of new Set(nrZuKurs.values())) {
    if (!kurse.has(gruppe)) {
      kurse.set(gruppe, { klasse: name, kuerzel: gruppe, fach: kursZuFach.get(gruppe) ?? '', lehrer: kursZuLehrer.get(gruppe) ?? '' });
    }
  }

  // <Pl>: jedes Kind-Element ist eine Stunde
  const stunden: Lesson[] = [];
  for (const std of kinder(kind(kl, 'Pl'))) {
    const stText = text(kind(std, 'St'));
    const fachEl = kind(std, 'Fa') ?? kind(std, 'fach');
    const raumEl = kind(std, 'Ra') ?? kind(std, 'raum');
    const lehrerEl = kind(std, 'Le') ?? kind(std, 'lehrer');

    const fach = text(fachEl);
    const info = text(kind(std, 'If') ?? kind(std, 'info') ?? kind(std, 'bemerkung'));
    const nr = text(kind(std, 'Nr') ?? kind(std, 'nummer'));

    const lehrerRoh = text(lehrerEl);
    // Technische Platzhalter wie "LeGeaendert" ausfiltern. (Nicht über "Änd" suchen: "Händel" ist ein Name.)
    const lehrer = istPlatzhalter(lehrerRoh) ? '' : lehrerRoh;

    if (stText.trim() === '' && fach.trim() === '' && info.trim() === '') continue;

    const raum = text(raumEl);
    const nurStr = nurStriche(fach);
    const infoText = info.toLowerCase();
    const gesamtText = (std.textContent ?? '').toLowerCase();

    // Priorität 1: Infotext-Keywords (ENTFALL). Geprüft werden das Info-Feld und der gesamte Text
    // der Stunde, falls die Info in einem anderen Unter-Tag gelandet ist. Kurze Wörter ("eva",
    // "frei", "selbst") nur als GANZES Wort werten.
    const hatEntfallInfo =
      ENTFALL_WOERTER.test(infoText) ||
      ENTFALL_WOERTER_LEHRER.test(lehrerRoh.toLowerCase()) ||
      ENTFALL_PHRASEN.some((p) => infoText.includes(p) || gesamtText.includes(p)) ||
      KEIN_UNTERRICHT.test(gesamtText) ||
      FAELLT_AUS.test(gesamtText);

    // Priorität 2: "---" in Fach oder Lehrer (ENTFALL)
    const hatStrich = fach === '---' || lehrer === '---';

    // Indiware-interne Flags & leere Fächer (ENTFALL)
    const istAusfallFlag =
      attr(std, 'Ausfall') === '1' || (attr(std, 'Ae') === '1' && (fach.trim() === '' || nurStr)) || fach.trim() === '';

    // Klausuren stehen im Plan als Hinweistext ("Klausur"). Sie sind Präsenz-Termine und dürfen nicht
    // wegen Wörtern wie "Aufgaben" als Ausfall gelten – nur bei ausdrücklichem Entfall.
    const istKlausur = KLAUSUR_WOERTER.test(gesamtText);
    const explizitEntfall = ENTFALL_PHRASEN.some((p) => infoText.includes(p)) || hatStrich;
    const entfaelltFinal = istKlausur ? explizitEntfall : hatEntfallInfo || hatStrich || istAusfallFlag;

    let fachGeaendert = hatAttribut(fachEl, 'FaAe');
    const raumGeaendertAttribut = hatAttribut(raumEl, 'RaAe');
    let lehrerGeaendert = hatAttribut(lehrerEl, 'LeAe');

    const hatAufgaben =
      infoText.includes('aufgab') || infoText.includes('erteilt') || infoText.includes('hausaufg') || infoText.includes('moodle');

    // Wenn Aufgaben explizit in Moodle stehen, soll die Änderung nicht als "Vertretung" markiert werden.
    if (infoText.includes('moodle')) {
      lehrerGeaendert = false;
      fachGeaendert = false;
    }

    // Manche Pläne tragen statt des alten Raums/Lehrers nur einen Platzhalter ("RaGeaendert") in RaAe/LeAe.
    const echterWert = (t: string | null | undefined): string | null => {
      const s = t?.trim();
      if (!s) return null;
      const k = s.toLowerCase();
      return k.includes('geaendert') || k.includes('geändert') ? null : s;
    };
    const originalRoom = raumGeaendertAttribut ? echterWert(raumEl?.getAttribute('RaAe')) : null;
    const originalTeacher = lehrerGeaendert ? echterWert(lehrerEl?.getAttribute('LeAe')) : null;

    // Priorität 3, 4 & 5: Status-Zuordnung und leeres Raumfeld-Schutz
    const raumGeaendert = raumGeaendertAttribut && raum.trim() !== '';

    const status: LessonStatus = entfaelltFinal ? 'ENTFALL' : lehrerGeaendert ? 'VERTRETUNG' : raumGeaendert ? 'RAUMAENDERUNG' : 'NORMAL';

    stunden.push({
      stunde: toIntOrNull(stText) ?? 0,
      beginn: parseUhrzeit(text(kind(std, 'Beginn'))),
      ende: parseUhrzeit(text(kind(std, 'Ende'))),
      fach,
      fachGeaendert,
      raum,
      raumGeaendert,
      originalRoom,
      lehrer,
      lehrerGeaendert,
      originalTeacher,
      info,
      entfaellt: entfaelltFinal,
      hatAufgaben,
      status,
      unterrichtsNr: nr.trim() === '' ? null : nr,
      kursKuerzel: nrZuKurs.get(nr) ?? null,
      klasse: name,
      istKlausur
    });
  }
  stunden.sort((a, b) => a.stunde - b.stunde);

  // Sammel-Eintrag, damit auch Klassen ohne Kurssystem wählbar sind – und als Notausgang, falls die
  // Kurszuordnung an einer Schule anders funktioniert.
  const ganzeKlasse: KursInfo = { klasse: name, kuerzel: GANZE_KLASSE, fach: '', lehrer: '' };

  return {
    klasse: name,
    stunden,
    kurse: [ganzeKlasse, ...[...kurse.values()].sort((a, b) => textVergleich(a.kuerzel, b.kuerzel))]
  };
}

void hatBuchstabeOderZiffer;

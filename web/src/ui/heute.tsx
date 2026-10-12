import type { ComponentChildren } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { alsEintraege, blockErste, blockStundenText, blockZusammengefasst } from '../logic/bloecke';
import { anwesenheit } from '../logic/freizeit';
import { berechneUebersicht, istUeberfaellig, pruefungsArtText, tageBis, type Pruefung, type Uebersicht } from '../logic/aufgaben';
import { aenderungsText } from '../logic/aenderungen';
import { ferienText } from '../logic/ferien';
import { freiVor, freiblockStundenText, type Freiblock } from '../logic/freizeit';
import { hinweisKurz } from '../logic/hinweis';
import { type Lesson, type NaechsteStundeErgebnis, hatAenderung, naechsteStunde, schluss } from '../logic/models';
import { schultagVersetzt } from '../logic/raster';
import { type Datum, countdownText, heute as heuteTag, kurzesDatum, langesDatum, plusTage, uhrzeitText, wochentagName } from '../logic/zeit';
import { aenderungenSeitGestern, aufgabenAblage, designAendern, designAblage, freundeAblage, pruefungenAblage } from '../services/daten';
import type { PersoenlicherPlan } from '../services/repo';
import { hatEigeneDaten, sicherungAblage, sicherungAlterText, sicherungFaellig, sicherungSpaeter, sicherungSpeichern } from '../services/sicherung';
import { useAblage } from '../services/speicher';
import { type TagAnsicht, tagNeuLaden, tagWischen, tagZuruecksetzen } from '../services/steuerung';
import { Badge, Chip, Icon, Karte, Laden, useFach, useHeute, useJetzt } from './basis';
import { FreundeKarte } from './freunde';
import { zeitraster } from '../logic/raster';
import { zeitrasterVon } from '../logic/models';

const ORANGE = '#FF9800';
const grauBadge = '#455A64';

const laeuft = (l: Lesson, jetzt: number) => l.beginn !== null && l.ende !== null && jetzt >= l.beginn && jetzt < l.ende;

/** Countdown der Hero-Karte: nur für heute. */
function countdownFuer(l: Lesson, jetzt: number, istHeute: boolean): string | null {
  if (!istHeute || l.beginn === null) return null;
  return countdownText((l.beginn - jetzt) * 60_000, l.ende === null ? null : (l.ende - jetzt) * 60_000);
}

function Info(p: { titel: string; wert: string; alt?: string | null; gewicht?: number }) {
  return (
    <div class="info" style={{ flex: p.gewicht ?? 1 }}>
      <div class="t">{p.titel}</div>
      <div class="w">
        <span class="ellipse">{p.wert}</span>
        {p.alt && <span class="alt">{p.alt}</span>}
      </div>
    </div>
  );
}

function HeroKarte(p: { ergebnis: NaechsteStundeErgebnis; istHeute: boolean; datum: Datum; jetzt: number }) {
  const { farbe, anzeige } = useFach();
  const l = p.ergebnis.lesson;
  const istEntfall = l.status === 'ENTFALL';
  const istVertretung = l.status === 'VERTRETUNG';
  const klausur = l.istKlausur && !istEntfall;
  // Bei einer Klausur ist ein anderer Name unter "Lehrer" die Aufsicht, keine Vertretung.
  const aufsicht = klausur && istVertretung;
  const gestoert = l.status !== 'NORMAL' && !aufsicht;
  const warnung = gestoert || klausur;
  const akzent = klausur ? 'var(--error)' : istEntfall ? 'var(--outline)' : istVertretung || l.status === 'RAUMAENDERUNG' ? ORANGE : farbe(l.fach);
  const hinweis = hinweisKurz(l) || (gestoert ? 'Änderung zum Regelplan' : '');
  const kopf = !p.istHeute
    ? `Als Nächstes · ${p.datum === plusTage(heuteTag(), 1) ? 'morgen' : wochentagName(p.datum).slice(0, 2) + '., ' + kurzesDatum(p.datum)}`
    : p.ergebnis.istVorschau
      ? 'Gleich vorbei · als Nächstes'
      : 'Nächste Stunde';
  const countdown = countdownFuer(l, p.jetzt, p.istHeute);
  return (
    <div class={`hero ${warnung ? 'warn' : ''}`}>
      <div class="schiene" style={{ background: akzent }} />
      <div class="koerper">
        <div class="zeile zwischen">
          <span class="titel-klein">{kopf}</span>
          {countdown && <Badge farbe={akzent} text="var(--surface)">{countdown}</Badge>}
        </div>
        <div class="fach ellipse">{anzeige(l.fach.trim() === '' ? '—' : l.fach)}</div>
        {(klausur || istEntfall) && (
          <Badge farbe="var(--error)" text="var(--on-error)">{klausur ? 'Klausur' : 'Fällt aus'}</Badge>
        )}
        <div class="infos">
          <Info titel="Raum" wert={istEntfall ? '—' : l.raum || '—'} alt={l.raumGeaendert ? l.originalRoom : null} />
          {l.beginn !== null && (
            <Info
              titel={`${l.stunde}. Stunde`}
              wert={l.ende !== null ? `${uhrzeitText(l.beginn)}–${uhrzeitText(l.ende)}` : uhrzeitText(l.beginn)}
              gewicht={1.2}
            />
          )}
          {l.lehrer.trim() !== '' ? (
            <Info titel={aufsicht ? 'Aufsicht' : 'Lehrer'} wert={l.lehrer} alt={istVertretung && !aufsicht ? l.originalTeacher : null} />
          ) : (
            <div class="info" />
          )}
        </div>
        {hinweis && <div style={{ marginTop: 12, fontWeight: 600 }}>{hinweis}</div>}
        {l.hatAufgaben && (
          <div style={{ marginTop: 8 }}>
            <Badge farbe={grauBadge} text="#fff">Aufgaben erteilt</Badge>
          </div>
        )}
      </div>
    </div>
  );
}

export function StundenZeile(p: { l: Lesson; istNaechste: boolean; laeuftGerade: boolean; onClick?: () => void; stundenText?: string | null }) {
  const { farbe, anzeige } = useFach();
  const { l } = p;
  const entfall = l.status === 'ENTFALL';
  const vertretung = l.status === 'VERTRETUNG';
  const akzent = l.status === 'ENTFALL' ? '#9e9e9e' : l.status === 'NORMAL' ? farbe(l.fach) : ORANGE;
  const hervor = p.laeuftGerade || p.istNaechste;
  const hinweis = hinweisKurz(l);
  return (
    <div class={`std ${hervor ? 'hervor' : ''} ${entfall ? 'entfall' : ''}`} onClick={p.onClick} style={{ cursor: p.onClick ? 'pointer' : undefined }}>
      {vertretung && <div class="balken" />}
      <div class="zeit">
        <div class="a">{l.beginn !== null ? uhrzeitText(l.beginn) : `${l.stunde}.`}</div>
        <div class="b">{l.ende !== null ? uhrzeitText(l.ende) : ''}</div>
        {p.stundenText && <div class="b">{p.stundenText}</div>}
      </div>
      <div class="punkt" style={{ background: akzent }} />
      <div class="mitte">
        <div class="zeile">
          <span class={`name ellipse ${entfall ? 'durch' : ''}`}>{anzeige(l.fach.trim() === '' ? '—' : l.fach)}</span>
          {p.laeuftGerade && !entfall && <Badge farbe="var(--secondary)" text="var(--on-secondary)">jetzt</Badge>}
        </div>
        <div class="zeile">
          {l.lehrer && <span class={`sehr-klein ellipse ${entfall ? 'durch' : ''}`}>{l.lehrer}</span>}
          {vertretung && l.originalTeacher && <span class="sehr-klein durch" style={{ fontSize: 10 }}>{l.originalTeacher}</span>}
        </div>
        {hinweis && <div class="sehr-klein" style={{ color: 'inherit', opacity: .85 }}>{hinweis}</div>}
      </div>
      <div class="rechts">
        {l.istKlausur && !entfall && <Badge farbe="var(--error)" text="var(--on-error)">Klausur</Badge>}
        {l.hatAufgaben && <Badge farbe={grauBadge} text="#fff">Aufgaben erteilt</Badge>}
        {entfall ? (
          <Badge farbe="var(--error)" text="var(--on-error)">entfällt</Badge>
        ) : (
          <>
            {vertretung && <Badge farbe={ORANGE} text="#fff">Vertretung</Badge>}
            {l.raumGeaendert && l.originalRoom && <span class="alt">{l.originalRoom}</span>}
            {l.raum.trim() !== '' && <span class={`raum ${l.raumGeaendert ? 'neu' : ''}`}>{l.raum}</span>}
          </>
        )}
      </div>
    </div>
  );
}

export function FreistundenZeile({ block }: { block: Freiblock }) {
  const zeit = `${uhrzeitText(block.beginn)}–${uhrzeitText(block.ende)}`;
  return <div class="freizeile">{['Freistunde', freiblockStundenText(block), zeit].filter(Boolean).join(' · ')}</div>;
}

function UebersichtKarte(p: { titel: string; text: string; hervor: boolean; onClick?: () => void }) {
  return (
    <Karte art={p.hervor ? 'warn' : 'hoch'} onClick={p.onClick}>
      <div class="titel-klein">{p.titel}</div>
      <div>{p.text}</div>
    </Karte>
  );
}

function aufgabenText(u: Uebersicht): string {
  let s = u.offeneAufgaben === 1 ? '1 offene Aufgabe' : `${u.offeneAufgaben} offene Aufgaben`;
  const teile: string[] = [];
  if (u.ueberfaellig > 0) teile.push(`${u.ueberfaellig} überfällig`);
  if (u.bisMorgen > 0) teile.push(`${u.bisMorgen} bis morgen fällig`);
  if (teile.length) s += ' · ' + teile.join(', ');
  return s;
}

function pruefungText(p: Pruefung, heute: Datum): string {
  const name = [p.fach, p.titel].filter((x) => x.trim() !== '').join(' – ');
  const tage = tageBis(p, heute);
  const wann = tage === 0 ? 'heute' : tage === 1 ? 'morgen' : `in ${tage} Tagen`;
  return name === '' ? wann.charAt(0).toUpperCase() + wann.slice(1) : `${name} · ${wann}`;
}

/** Tagesplan als Text zum Teilen (z.B. per Messenger an Mitschüler). */
export function planAlsText(pp: PersoenlicherPlan): string {
  const zeilen = [langesDatum(pp.datum)];
  for (const l of pp.plan.stunden) {
    let z = `${l.beginn !== null ? uhrzeitText(l.beginn) : `${l.stunde}.`}  ${l.fach.trim() || l.kursKuerzel || '—'}`;
    if (l.entfaellt) z += ' – fällt aus';
    else if (l.istKlausur) z += ' – Klausur';
    else if (l.raum.trim()) z += ` (${l.raum})`;
    if (!l.entfaellt && l.status === 'VERTRETUNG' && l.lehrer.trim()) z += `, Vertretung: ${l.lehrer}`;
    zeilen.push(z);
  }
  const s = schluss(pp.plan);
  if (s !== null) zeilen.push(`Schluss: ${uhrzeitText(s)}`);
  return zeilen.join('\n');
}

/** Nach links wischen = nächster Tag, nach rechts = voriger – erst nach einer deutlichen Bewegung zur Seite. */
export function TagWischen(p: { onVor: () => void; onZurueck: () => void; children: ComponentChildren }) {
  const start = useRef<{ x: number; y: number } | null>(null);
  const aktuell = useRef(p);
  aktuell.current = p;
  return (
    <div
      onTouchStart={(e) => {
        const t = e.touches[0];
        start.current = { x: t.clientX, y: t.clientY };
      }}
      onTouchEnd={(e) => {
        const s = start.current;
        start.current = null;
        if (!s) return;
        const t = e.changedTouches[0];
        const dx = t.clientX - s.x;
        const dy = t.clientY - s.y;
        if (Math.abs(dx) < 80 || Math.abs(dx) < Math.abs(dy) * 1.5) return;
        if (dx < 0) aktuell.current.onVor();
        else aktuell.current.onZurueck();
      }}
      style={{ minHeight: '100%' }}
    >
      {p.children}
    </div>
  );
}

export function TagesLeiste(p: { datum: Datum; abweichend: boolean; basisText: string; onZurueck: () => void; onVor: () => void; onBasis: () => void }) {
  return (
    <div class="tagesleiste">
      <button class="icon-knopf" aria-label="Voriger Tag" onClick={p.onZurueck}>
        <Icon name="links" />
      </button>
      <div class="mitte">
        <div style={{ fontWeight: 600 }}>{langesDatum(p.datum)}</div>
        {p.abweichend && (
          <button class="textknopf" onClick={p.onBasis}>
            {p.basisText}
          </button>
        )}
      </div>
      <button class="icon-knopf" aria-label="Nächster Tag" onClick={p.onVor}>
        <Icon name="rechts" />
      </button>
    </div>
  );
}

/** Der Plan eines Tages mit Hero-Karte, Hinweisen, Übersicht und der Stundenliste. */
function Tagesplan(p: {
  pp: PersoenlicherPlan;
  onAufgaben: () => void;
  onKlausuren: () => void;
  onStunde: (l: Lesson, datum: Datum) => void;
  onFreund: (id: string) => void;
  onGemeinsam: () => void;
  aktualisiert: boolean;
}) {
  const jetzt = useJetzt();
  const heute = useHeute();
  const design = useAblage(designAblage);
  const aufgaben = useAblage(aufgabenAblage);
  const pruefungen = useAblage(pruefungenAblage);
  const freunde = useAblage(freundeAblage);
  const sicherung = useAblage(sicherungAblage);
  const { pp } = p;
  const plan = pp.plan;
  const istHeute = pp.istHeute && pp.datum === heute;
  // Heute wird "nächste Stunde" mit der tickenden Uhr neu bestimmt.
  const naechste = istHeute ? naechsteStunde(plan, jetzt) : pp.naechste;
  const raster = zeitrasterVon(pp.gesamt);
  const freiVorMap = freiVor(plan, raster);
  const eintraege = alsEintraege(plan.stunden, design.bloecke);
  const uebersicht = berechneUebersicht(aufgaben, pruefungen, heute, jetzt);
  const tagVorbei = istHeute && naechste === null;
  const ferien = ferienText(heute);
  const zeitstempel = new Date(pp.geprueftUm).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  void anwesenheit;
  void zeitraster;
  // Änderungen gegenüber dem Stand eines früheren Tages.
  const seitGestern = useMemo(() => aenderungenSeitGestern(pp.datum, plan.stunden), [pp.datum, plan]);

  return (
    <div class="seite">
      {pp.ausCache && (
        <Karte art="info">
          Gespeicherter Stand von {zeitstempel} · {p.aktualisiert ? 'wird aktualisiert …' : 'keine Verbindung, Plan kann veraltet sein'}
        </Karte>
      )}
      {sicherungFaellig(Date.now(), sicherung, hatEigeneDaten()) && (
        <Karte art="info">
          <div style={{ fontWeight: 600 }}>Sicherung fällig</div>
          <div>
            {sicherung.letzte === 0 ? 'Du hast noch keine Sicherung gemacht.' : `Deine letzte Sicherung war ${sicherungAlterText(sicherung.letzte, Date.now())}.`} Speichere sie als Datei, falls der Browser seine Daten verliert.
          </div>
          <div class="zeile" style={{ paddingTop: 6 }}>
            <button class="textknopf" onClick={sicherungSpeichern}>Jetzt sichern</button>
            <button class="textknopf" onClick={sicherungSpaeter}>Später</button>
          </div>
        </Karte>
      )}
      {ferien && <div class="klein" style={{ fontWeight: 600 }}>🏖️ {ferien} (ohne Gewähr)</div>}

      {naechste ? (
        <HeroKarte ergebnis={naechste} istHeute={istHeute} datum={pp.datum} jetzt={jetzt} />
      ) : (
        <Karte style={{ borderRadius: 24, padding: 24 }}>
          <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--on-surface)' }}>{tagVorbei ? 'Schluss für heute' : 'Frei'}</div>
          <div>
            {tagVorbei && p.aktualisiert
              ? 'Der nächste Schultag wird geladen …'
              : tagVorbei
                ? 'Für die nächsten Tage ist in deinen Kursen noch kein Unterricht eingetragen.'
                : 'In den nächsten Tagen ist in deinen Kursen kein Unterricht eingetragen.'}
          </div>
        </Karte>
      )}

      {seitGestern.length > 0 && <UebersichtKarte titel="Seit gestern geändert" text={aenderungsText(seitGestern)} hervor={false} />}
      {plan.hinweise.length > 0 && (
        <UebersichtKarte
          titel={`Hinweise für ${istHeute ? 'heute' : 'diesen Tag'}`}
          text={plan.hinweise.join('\n')}
          hervor={plan.hinweise.some((h) => h.toLowerCase().includes('klausur'))}
        />
      )}
      {uebersicht.offeneAufgaben > 0 && <UebersichtKarte titel="Hausaufgaben" text={aufgabenText(uebersicht)} hervor={uebersicht.ueberfaellig > 0} onClick={p.onAufgaben} />}
      {uebersicht.naechstePruefung && (
        <UebersichtKarte
          titel={pruefungsArtText(uebersicht.naechstePruefung.art)}
          text={pruefungText(uebersicht.naechstePruefung, heute)}
          hervor={tageBis(uebersicht.naechstePruefung, heute) <= 1}
          onClick={p.onKlausuren}
        />
      )}
      {freunde.length > 0 && <FreundeKarte freunde={freunde} persoenlich={pp} onFreund={p.onFreund} onWoche={p.onGemeinsam} />}

      <div class="zeile zwischen" style={{ paddingTop: 6 }}>
        <span style={{ fontWeight: 600, fontSize: 16 }}>
          {istHeute ? 'Heute' : wochentagName(pp.datum).slice(0, 2) + '., ' + kurzesDatum(pp.datum)}
          {schluss(plan) !== null && ` · Schluss ${uhrzeitText(schluss(plan)!)}`}
        </span>
        <span class="sehr-klein">{langesDatum(pp.datum)}</span>
      </div>
      <div class="zeile">
        <Chip an={!design.bloecke} onClick={() => designAendern({ bloecke: false })}>Stunden</Chip>
        <Chip an={design.bloecke} onClick={() => designAendern({ bloecke: true })}>Blöcke</Chip>
      </div>

      {eintraege.map((block, i) => {
        const l = blockZusammengefasst(block);
        return (
          <div key={`${block.ersteIndex}-${i}`}>
            {freiVorMap.get(block.ersteIndex) && <FreistundenZeile block={freiVorMap.get(block.ersteIndex)!} />}
            <StundenZeile
              l={l}
              istNaechste={block.stunden.some((s) => s.stunde === naechste?.lesson.stunde)}
              laeuftGerade={istHeute && laeuft(l, jetzt)}
              onClick={() => p.onStunde(blockErste(block), pp.datum)}
              stundenText={design.bloecke ? blockStundenText(block) : null}
            />
          </div>
        );
      })}

      <div class="sehr-klein" style={{ paddingTop: 8 }}>
        {plan.klasse && <div>{plan.klasse}</div>}
        {plan.kopf.zeitstempel && <div>Plan der Schule: {plan.kopf.zeitstempel}</div>}
        <div>Tipp: Stunde antippen, um eine Hausaufgabe bis zur nächsten Stunde einzutragen.</div>
        <div>App zuletzt geprüft: {zeitstempel}</div>
      </div>
    </div>
  );
}

export function Heute(p: {
  pp: PersoenlicherPlan;
  tag: TagAnsicht | null;
  aktualisiert: boolean;
  onAufgaben: () => void;
  onKlausuren: () => void;
  onStunde: (l: Lesson, datum: Datum) => void;
  onFreund: (id: string) => void;
  onGemeinsam: () => void;
}) {
  const abweichend = p.tag && p.tag.datum !== p.pp.datum ? p.tag : null;
  const angezeigt = abweichend?.datum ?? p.pp.datum;
  const wischen = (r: number) => tagWischen(r, angezeigt, p.pp.datum, schultagVersetzt);
  useEffect(() => {
    // Beim Zurückkehren in die App den angesehenen Tag nicht veraltet stehen lassen.
    void tagNeuLaden;
  }, []);
  const [, neu] = useState(0);
  void neu;
  return (
    <>
      <TagesLeiste
        datum={angezeigt}
        abweichend={abweichend !== null}
        basisText={p.pp.datum === heuteTag() ? 'Zurück zu heute' : 'Zurück'}
        onZurueck={() => wischen(-1)}
        onVor={() => wischen(1)}
        onBasis={tagZuruecksetzen}
      />
      <TagWischen onVor={() => wischen(1)} onZurueck={() => wischen(-1)}>
        {abweichend?.art === 'LAEDT' ? (
          <Laden />
        ) : abweichend?.art === 'LEER' ? (
          <div class="vollseite">
            <div>{abweichend.grund}</div>
            <div class="sehr-klein">Wische weiter, um andere Tage zu sehen.</div>
          </div>
        ) : (
          <Tagesplan {...p} pp={abweichend?.art === 'FERTIG' ? abweichend.plan : p.pp} />
        )}
      </TagWischen>
    </>
  );
}

void istUeberfaellig;
void hatAenderung;

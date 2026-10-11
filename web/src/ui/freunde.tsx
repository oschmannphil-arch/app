import { useEffect, useMemo, useState } from 'preact/hooks';
import { blockErste, blockZusammengefasst, alsEintraege } from '../logic/bloecke';
import {
  type Freiblock,
  type Freund,
  anwesenheit,
  anwesenheitText,
  belegt,
  freiVor,
  freiblockStundenText,
  gemeinsamFrei,
  gemeinsamFreiAlle,
  gemeinsameStunden,
  inDerSchule,
  jetztFrei,
  zusammenKey
} from '../logic/freizeit';
import { type KursInfo, type TagesPlan, tagesplanFuer, zeitrasterVon } from '../logic/models';
import type { Zeitfenster } from '../logic/raster';
import { wochenReferenz } from '../logic/raster';
import { type Datum, heute as heuteTag, langesDatum, uhrzeitText, wochentagName } from '../logic/zeit';
import { webLink, nachricht } from '../logic/freundteilen';
import { designAblage, designAendern, freundLoeschen, freundSpeichern, kursAblage, freundeAblage } from '../services/daten';
import { type PersoenlicherPlan, holeWoche } from '../services/repo';
import { neueId, useAblage } from '../services/speicher';
import { zugangAblage } from '../services/daten';
import { Chip, Dialog, Karte, Laden, teilen, useJetzt } from './basis';
import { FreistundenZeile, StundenZeile } from './heute';
import { KursListe } from './kurse';

const zeitraum = (von: number, bis: number) => `${uhrzeitText(von)}–${uhrzeitText(bis)}`;
const tagLabel = (pp: PersoenlicherPlan) => (pp.istHeute ? 'heute' : wochentagName(pp.datum));

/** "3.–4. Std (08:45–11:00)" bzw. nur die Uhrzeit, wenn der Plan keine Stundennummern hergibt. */
export function blockText(b: Freiblock): string {
  const s = freiblockStundenText(b);
  return s ? `${s} (${zeitraum(b.beginn, b.ende)})` : zeitraum(b.beginn, b.ende);
}

interface FreundHeute {
  unterricht: string | null;
  frei: Freiblock[];
}

function freundHeute(eigen: TagesPlan, freund: TagesPlan, raster: Zeitfenster[]): FreundHeute {
  const b = belegt(freund);
  if (b.length === 0) return { unterricht: null, frei: [] };
  return { unterricht: zeitraum(b[0][0], b[b.length - 1][1]), frei: gemeinsamFrei(eigen, freund, raster) };
}

/** Karte auf der Startseite: je Freund die Unterrichtszeit und gemeinsame Freistunden. */
export function FreundeKarte(p: { freunde: Freund[]; persoenlich: PersoenlicherPlan; onFreund: (id: string) => void; onWoche: () => void }) {
  const jetzt = useJetzt();
  const { persoenlich: pp } = p;
  const raster = zeitrasterVon(pp.gesamt);
  const tage = useMemo(() => p.freunde.map((f) => [f, tagesplanFuer(pp.gesamt, new Set(f.kurse))] as const), [p.freunde, pp]);
  const heute = useMemo(() => tage.map(([, plan]) => freundHeute(pp.plan, plan, raster)), [tage, pp]);
  const gerade = pp.istHeute ? tage.flatMap(([f, plan]) => ((b) => (b ? [[f, b] as const] : []))(jetztFrei(plan, raster, jetzt))) : [];
  // Vor Schulbeginn und nach Schulschluss aller Freunde wäre "niemand frei" irreführend.
  const jemandDa = pp.istHeute && tage.some(([, plan]) => inDerSchule(plan, jetzt));
  return (
    <Karte art="hoch">
      <div class="titel-klein">Freunde · {tagLabel(pp)}</div>
      {(gerade.length > 0 || jemandDa) && (
        <div style={{ fontWeight: 600, padding: '4px 0 2px', color: gerade.length === 0 ? 'var(--on-surface-variant)' : 'var(--primary)' }}>
          {gerade.length === 0 ? 'Gerade hat niemand eine Freistunde.' : 'Gerade frei: ' + gerade.map(([f, b]) => `${f.name} (bis ${uhrzeitText(b.ende)})`).join(', ')}
        </div>
      )}
      {tage.map(([freund, plan], i) => {
        const h = heute[i];
        return (
          <div key={freund.id} class="stapel" style={{ padding: '8px 0', gap: 2, cursor: 'pointer' }} onClick={() => p.onFreund(freund.id)}>
            <div class="zeile">
              <span class="fuell" style={{ fontSize: 16, fontWeight: 500, color: 'var(--on-surface)' }}>{freund.name}</span>
              {h.unterricht !== null && (
                <span class="sehr-klein">{pp.istHeute ? anwesenheitText(anwesenheit(plan, jetzt)) : `in der Schule ${h.unterricht}`}</span>
              )}
            </div>
            {h.unterricht === null ? (
              <div>Kein Unterricht an diesem Tag</div>
            ) : h.frei.length === 0 ? (
              <div>Keine gemeinsame Freistunde</div>
            ) : (
              <>
                <div class="sehr-klein">Gemeinsam frei</div>
                {h.frei.map((b, k) => (
                  <div key={k} style={{ fontWeight: 600, fontSize: 16, color: 'var(--primary)' }}>{blockText(b)}</div>
                ))}
              </>
            )}
          </div>
        );
      })}
      <button class="textknopf" onClick={p.onWoche}>Wann haben wir diese Woche frei?</button>
    </Karte>
  );
}

/** Der Tag eines Freundes: gemeinsame Freistunden, gemeinsame Stunden und sein Plan. */
export function FreundTag(p: { freund: Freund; persoenlich: PersoenlicherPlan; onWoche: () => void; onBearbeiten: () => void }) {
  const jetzt = useJetzt();
  const design = useAblage(designAblage);
  const { freund, persoenlich: pp } = p;
  const plan = useMemo(() => tagesplanFuer(pp.gesamt, new Set(freund.kurse)), [freund, pp]);
  const raster = zeitrasterVon(pp.gesamt);
  const frei = gemeinsamFrei(pp.plan, plan, raster);
  const freiVorMap = freiVor(plan, raster);
  const eintraege = alsEintraege(plan.stunden, design.bloecke);
  const zusammen = new Set(gemeinsameStunden(plan, pp.plan).map(zusammenKey));
  return (
    <div class="seite">
      <Karte art="hoch">
        <div class="titel-klein">Gemeinsam frei · {tagLabel(pp)}</div>
        <div style={{ whiteSpace: 'pre-line' }}>{frei.length === 0 ? 'Keine gemeinsame Freistunde.' : frei.map(blockText).join('\n')}</div>
      </Karte>
      {plan.stunden.length === 0 && <div class="klein">{freund.name} hat an diesem Tag keinen Unterricht in den gewählten Kursen.</div>}
      {plan.stunden.length > 0 && (
        <div class="zeile">
          <Chip an={!design.bloecke} onClick={() => designAendern({ bloecke: false })}>Stunden</Chip>
          <Chip an={design.bloecke} onClick={() => designAendern({ bloecke: true })}>Blöcke</Chip>
        </div>
      )}
      {eintraege.map((block, i) => {
        const l = blockZusammengefasst(block);
        return (
          <div key={`${block.ersteIndex}-${i}`}>
            {freiVorMap.get(block.ersteIndex) && <FreistundenZeile block={freiVorMap.get(block.ersteIndex)!} />}
            {block.stunden.some((s) => zusammen.has(zusammenKey(s))) && (
              <div class="sehr-klein" style={{ color: 'var(--primary)', paddingLeft: 4, paddingBottom: 2 }}>zusammen mit dir</div>
            )}
            <StundenZeile
              l={l}
              istNaechste={false}
              laeuftGerade={pp.istHeute && l.beginn !== null && l.ende !== null && jetzt >= l.beginn && jetzt < l.ende}
            />
          </div>
        );
      })}
      <button class="knopf voll breit" onClick={p.onWoche}>Wann haben wir diese Woche frei?</button>
      <button class="knopf breit" onClick={p.onBearbeiten}>Kurse von {freund.name} bearbeiten</button>
    </div>
  );
}

interface TagErgebnis {
  frei: Freiblock[];
  ohneUnterricht: string[];
}

interface WocheTag {
  datum: Datum;
  raster: Zeitfenster[];
  eigen: TagesPlan | null;
  freunde: Map<string, TagesPlan>;
  fehler: string | null;
}

/** Zeiten, in denen du und alle Gewählten frei haben; null, wenn es keinen Plan für den Tag gibt. */
function gemeinsamAm(tag: WocheTag, gewaehlte: Freund[]): TagErgebnis | null {
  if (!tag.eigen) return null;
  const teilnehmer: Array<[string, TagesPlan]> = [['Du', tag.eigen], ...gewaehlte.map((f): [string, TagesPlan] => [f.name, tag.freunde.get(f.id)!])];
  const ohne = teilnehmer.filter(([, plan]) => plan.stunden.every((l) => l.entfaellt)).map(([n]) => n);
  if (ohne.length > 0) return { frei: [], ohneUnterricht: ohne };
  return { frei: gemeinsamFreiAlle(teilnehmer.map(([, plan]) => plan), tag.raster), ohneUnterricht: [] };
}

function ohneUnterrichtText(namen: string[]): string {
  const verb = namen.length === 1 && namen[0] === 'Du' ? 'hast' : namen.length === 1 ? 'hat' : 'haben';
  return `${namen.join(' und ')} ${verb} an diesem Tag keinen Unterricht`;
}

/**
 * Wann haben du und (mehrere) Freunde in der ganzen Woche gleichzeitig frei? Freunde lassen sich einzeln
 * an- und abwählen; gezeigt werden nur Zeiten, in denen ALLE Gewählten frei sind.
 */
export function GemeinsamFrei(p: { freunde: Freund[]; startAuswahl: string[] }) {
  const [auswahl, setAuswahl] = useState<string[]>(p.startAuswahl);
  const [naechste, setNaechste] = useState(false);
  const [tage, setTage] = useState<WocheTag[] | null>(null);
  const [fehler, setFehler] = useState<string | null>(null);
  const kurse = useAblage(kursAblage);

  const laden = () => {
    const z = zugangAblage.get();
    if (!z) return;
    setTage(null);
    setFehler(null);
    void holeWoche(z, wochenReferenz(naechste ? 1 : 0, heuteTag())).then((ergebnisse) => {
      if (ergebnisse.every(([, e]) => e.art === 'NETZ_FEHLER')) {
        setFehler('Laden fehlgeschlagen.');
        return;
      }
      setTage(
        ergebnisse.map(([datum, e]): WocheTag => {
          if (e.art !== 'OK') return { datum, raster: [], eigen: null, freunde: new Map(), fehler: e.art === 'KEIN_PLAN' ? 'Kein Plan' : 'Laden fehlgeschlagen' };
          return {
            datum,
            raster: zeitrasterVon(e.plan),
            eigen: tagesplanFuer(e.plan, new Set(kurse)),
            freunde: new Map(p.freunde.map((f) => [f.id, tagesplanFuer(e.plan, new Set(f.kurse))])),
            fehler: null
          };
        })
      );
    });
  };
  useEffect(laden, [naechste]);

  const gewaehlte = p.freunde.filter((f) => auswahl.includes(f.id));
  const ergebnisse = (tage ?? []).map((t) => [t, gemeinsamAm(t, gewaehlte)] as const);
  const mit = ergebnisse.filter(([, e]) => e && e.frei.length > 0).length;
  const moeglich = ergebnisse.filter(([, e]) => e).length;
  const wer = gewaehlte.length === 1 ? 'ihr' : 'ihr alle';
  return (
    <div class="seite">
      <div class="zeile">
        <Chip an={!naechste} onClick={() => setNaechste(false)}>Diese Woche</Chip>
        <Chip an={naechste} onClick={() => setNaechste(true)}>Nächste Woche</Chip>
      </div>
      <div class="klein">Mit wem willst du dich treffen? Pro Tag siehst du, welche Stunden ihr alle gleichzeitig frei habt.</div>
      <div class="scroll-x">
        {p.freunde.map((f) => (
          <Chip key={f.id} an={auswahl.includes(f.id)} onClick={() => setAuswahl(auswahl.includes(f.id) ? auswahl.filter((x) => x !== f.id) : [...auswahl, f.id])}>
            {f.name}
          </Chip>
        ))}
      </div>
      {gewaehlte.length === 0 ? (
        <div class="klein">Wähle mindestens einen Freund aus.</div>
      ) : fehler ? (
        <>
          <div>{fehler}</div>
          <button class="knopf" onClick={laden}>Erneut versuchen</button>
        </>
      ) : tage === null ? (
        <Laden />
      ) : (
        <>
          <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--on-surface)' }}>
            {moeglich === 0 ? 'Für diese Woche gibt es noch keine Pläne.' : mit === 0 ? `In dieser Woche habt ${wer} keine gemeinsame Freistunde.` : `An ${mit} von ${moeglich} Tagen habt ${wer} gemeinsam frei.`}
          </div>
          {ergebnisse.map(([t, e]) => (
            <Karte key={t.datum}>
              <div class="titel-klein" style={{ color: 'var(--on-surface)' }}>{langesDatum(t.datum)}</div>
              {e === null ? (
                <div>{t.fehler ?? 'Kein Plan'}</div>
              ) : e.ohneUnterricht.length > 0 ? (
                <div>{ohneUnterrichtText(e.ohneUnterricht)}</div>
              ) : e.frei.length === 0 ? (
                <div>Keine gemeinsame Freistunde</div>
              ) : (
                e.frei.map((b, i) => <div key={i} style={{ color: 'var(--primary)' }}>Frei: {blockText(b)}</div>)
              )}
            </Karte>
          ))}
        </>
      )}
    </div>
  );
}

/** Freund anlegen oder bearbeiten: Name und Kurse. */
export function FreundBearbeiten(p: {
  freund: Freund;
  istNeu: boolean;
  kurse: KursInfo[];
  onSpeichern: (f: Freund) => void;
  onLoeschen: () => void;
  onAbbrechen: () => void;
}) {
  const [name, setName] = useState(p.freund.name);
  const [auswahl, setAuswahl] = useState<string[]>(p.freund.kurse);
  const [hinweis, setHinweis] = useState('');
  return (
    <div class="seite">
      <label class="feld">
        Name
        <input type="text" value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
      </label>
      <div class="klein">Kreuze die Kurse an. Die App zeigt dann den Plan und wann ihr gemeinsam frei habt.</div>
      <KursListe kurse={p.kurse} auswahl={auswahl} onAuswahl={setAuswahl} />
      <button class="knopf voll breit" disabled={name.trim() === '' || auswahl.length === 0} onClick={() => p.onSpeichern({ ...p.freund, name: name.trim(), kurse: auswahl })}>
        {name.trim() === '' ? 'Namen eingeben' : auswahl.length === 0 ? 'Mindestens einen Kurs wählen' : 'Speichern'}
      </button>
      {!p.istNeu && (
        <button
          class="knopf breit"
          onClick={async () => setHinweis(await teilen(nachricht(location.href, name, auswahl, false), 'Kurse teilen'))}
        >
          Kurse als Link teilen
        </button>
      )}
      {hinweis && <div class="klein">{hinweis}</div>}
      <div class="zeile">
        <button class="knopf fuell" onClick={p.onAbbrechen}>Abbrechen</button>
        {!p.istNeu && (
          <button class="knopf fuell" style={{ color: 'var(--error)' }} onClick={p.onLoeschen}>Löschen</button>
        )}
      </div>
    </div>
  );
}

/** Abschnitt in den Einstellungen: Freunde verwalten. */
export function FreundeEinstellungen(p: { freunde: Freund[]; onBearbeiten: (f: Freund | null) => void; onLink: (text: string) => string }) {
  const [linkDialog, setLinkDialog] = useState(false);
  const [text, setText] = useState('');
  const [meldung, setMeldung] = useState('');
  return (
    <Karte>
      <div class="stapel">
        <div style={{ fontSize: 18, fontWeight: 500, color: 'var(--on-surface)' }}>Freunde</div>
        <div class="klein">Trag die Kurse von Freunden ein – dann zeigt die Startseite, wann ihr gemeinsam frei habt.</div>
        {p.freunde.map((f) => (
          <div key={f.id} class="zeile" style={{ padding: '8px 0', cursor: 'pointer' }} onClick={() => p.onBearbeiten(f)}>
            <span class="fuell" style={{ color: 'var(--on-surface)', fontSize: 16 }}>{f.name}</span>
            <span class="sehr-klein">{f.kurse.length === 1 ? '1 Kurs' : `${f.kurse.length} Kurse`}</span>
          </div>
        ))}
        <button class="knopf breit" onClick={() => p.onBearbeiten(null)}>Freund hinzufügen</button>
        <button class="knopf breit" onClick={() => { setText(''); setMeldung(''); setLinkDialog(true); }}>Link von einem Freund einfügen</button>
      </div>
      {linkDialog && (
        <Dialog
          titel="Link von einem Freund"
          onZu={() => setLinkDialog(false)}
          knoepfe={
            <>
              <button class="textknopf" onClick={() => setLinkDialog(false)}>Abbrechen</button>
              <button
                class="textknopf"
                disabled={text.trim() === ''}
                onClick={() => {
                  const m = p.onLink(text);
                  if (m === '') setLinkDialog(false);
                  else setMeldung(m);
                }}
              >
                Übernehmen
              </button>
            </>
          }
        >
          <div>Füge den Link oder die ganze Nachricht deines Freundes ein.</div>
          <textarea rows={4} value={text} onInput={(e) => setText((e.target as HTMLTextAreaElement).value)} />
          {meldung && <div style={{ color: 'var(--error)' }}>{meldung}</div>}
        </Dialog>
      )}
    </Karte>
  );
}

export const neuerFreund = (): Freund => ({ id: neueId(), name: '', kurse: [] });
export { freundLoeschen, freundSpeichern, freundeAblage, webLink, blockErste };

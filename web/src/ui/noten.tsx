import { useState } from 'preact/hooks';
import { type Pruefung, pruefungIstVorbei } from '../logic/aufgaben';
import {
  ANTEIL_MAX,
  ANTEIL_MIN,
  Abi,
  type FachSchnitt,
  type Note,
  type NotenArt,
  type NotenSystem,
  SYSTEME,
  alsSchulnote,
  benoetigt,
  durchschnittAllerHalbjahre,
  fachSchluessel,
  gesamtSchnitt,
  halbjahr as halbjahrVon,
  halbjahrDavor,
  nachFach,
  notenArtText,
  prognose,
  zielErreicht,
  zeugnis
} from '../logic/noten';
import { type Datum, deutschesDatum, ganzKurzesDatum } from '../logic/zeit';
import { notenAblage, notenAnteilAblage, notenBearbeiten, notenHinzufuegen, notenLoeschen, notenZieleAblage } from '../services/daten';
import { useAblage } from '../services/speicher';
import { Chip, Dialog, Icon, Karte, LeerHinweis, komma, useFach, useHeute, useJetzt } from './basis';

interface Entwurf {
  bestehend?: Note;
  fach?: string;
  art: NotenArt;
  datum: Datum;
}

const ARTEN: NotenArt[] = ['KLAUSUR', 'MUENDLICH'];
const wertText = (system: NotenSystem, w: number) => (system === 'PUNKTE' ? `${w} P` : `Note ${w}`);

function NoteDialog(p: { system: NotenSystem; entwurf: Entwurf; vorschlaege: string[]; onZu: () => void; onSpeichern: (fach: string, punkte: number, art: NotenArt, datum: Datum, notiz: string) => void }) {
  const alt = p.entwurf.bestehend;
  const { min, max } = SYSTEME[p.system];
  const [fach, setFach] = useState(alt?.fach ?? p.entwurf.fach ?? '');
  const [text, setText] = useState(alt ? String(alt.punkte) : '');
  const [art, setArt] = useState<NotenArt>(alt?.art ?? p.entwurf.art);
  const [datum, setDatum] = useState<Datum>(alt?.datum ?? p.entwurf.datum);
  const [notiz, setNotiz] = useState(alt?.notiz ?? '');
  const zahl = /^\d{1,2}$/.test(text) ? parseInt(text, 10) : null;
  const punkte = zahl !== null && zahl >= min && zahl <= max ? zahl : null;
  return (
    <Dialog
      titel={alt ? 'Note bearbeiten' : 'Note eintragen'}
      onZu={p.onZu}
      knoepfe={
        <>
          <button class="textknopf" onClick={p.onZu}>Abbrechen</button>
          <button class="textknopf" disabled={fach.trim() === '' || punkte === null} onClick={() => punkte !== null && p.onSpeichern(fach, punkte, art, datum, notiz)}>Speichern</button>
        </>
      }
    >
      <div class="zeile">{ARTEN.map((a) => <Chip key={a} an={art === a} onClick={() => setArt(a)}>{notenArtText(a)}</Chip>)}</div>
      <label class="feld">Fach<input type="text" value={fach} onInput={(e) => setFach((e.target as HTMLInputElement).value)} /></label>
      {p.vorschlaege.length > 0 && !alt && <div class="scroll-x">{p.vorschlaege.map((v) => <Chip key={v} onClick={() => setFach(v)}>{v}</Chip>)}</div>}
      <label class="feld">
        {p.system === 'PUNKTE' ? 'Punkte (0 bis 15)' : 'Note (1 bis 6)'}
        <input type="text" inputMode="numeric" class={text !== '' && punkte === null ? 'fehler' : ''} value={text} onInput={(e) => setText((e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 2))} />
      </label>
      <label class="feld">Notiz (optional)<input type="text" value={notiz} onInput={(e) => setNotiz((e.target as HTMLInputElement).value)} /></label>
      <label class="feld">Datum<input type="date" value={datum} onInput={(e) => setDatum((e.target as HTMLInputElement).value || datum)} /></label>
    </Dialog>
  );
}

function AnteilRegler(p: { anteil: number; onAnteil: (a: number) => void }) {
  const [text, setText] = useState(String(p.anteil));
  const gueltig = (w: number) => w >= ANTEIL_MIN && w <= ANTEIL_MAX;
  const zahl = /^\d{1,3}$/.test(text) ? parseInt(text, 10) : null;
  // Erst als Fehler zeigen, wenn die Eingabe nicht mehr zu einer gültigen Zahl werden kann ("3" → "35").
  const fehler = zahl === null || zahl > ANTEIL_MAX || (text.length >= 2 && zahl < ANTEIL_MIN);
  return (
    <div class="stapel" style={{ gap: 2 }}>
      <div class="sehr-klein">Klausuren zählen</div>
      <div class="zeile">
        <input
          type="range"
          min={ANTEIL_MIN}
          max={ANTEIL_MAX}
          step={1}
          value={p.anteil}
          onInput={(e) => {
            const w = parseInt((e.target as HTMLInputElement).value, 10);
            setText(String(w));
            p.onAnteil(w);
          }}
        />
        <div style={{ width: 110, display: 'flex', alignItems: 'center', gap: 4 }}>
          <input
            type="text"
            inputMode="numeric"
            class={fehler ? 'fehler' : ''}
            value={text}
            onInput={(e) => {
              const t = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 3);
              setText(t);
              const w = /^\d+$/.test(t) ? parseInt(t, 10) : null;
              if (w !== null && gueltig(w)) p.onAnteil(w);
            }}
          />
          <span>%</span>
        </div>
      </div>
      <div class="sehr-klein">Mündlich zählt {100 - p.anteil} %. Erlaubt: {ANTEIL_MIN} bis {ANTEIL_MAX} %.</div>
    </div>
  );
}

function PrognoseDialog(p: { system: NotenSystem; fach: string; noten: Note[]; anteil: number; onZu: () => void }) {
  const { min, max } = SYSTEME[p.system];
  const [art, setArt] = useState<NotenArt>('KLAUSUR');
  const [text, setText] = useState('');
  const zahl = /^\d{1,2}$/.test(text) ? parseInt(text, 10) : null;
  const wert = zahl !== null && zahl >= min && zahl <= max ? zahl : null;
  const e = wert !== null ? prognose(p.fach, p.noten, p.system, art, wert, p.anteil) : null;
  return (
    <Dialog titel={`Was wäre wenn … (${p.fach})`} onZu={p.onZu} knoepfe={<button class="textknopf" onClick={p.onZu}>Schließen</button>}>
      <div class="zeile">{ARTEN.map((a) => <Chip key={a} an={art === a} onClick={() => setArt(a)}>{notenArtText(a)}</Chip>)}</div>
      <label class="feld">
        {p.system === 'PUNKTE' ? 'Angenommene Punkte (0 bis 15)' : 'Angenommene Note (1 bis 6)'}
        <input type="text" inputMode="numeric" class={text !== '' && wert === null ? 'fehler' : ''} value={text} onInput={(ev) => setText((ev.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 2))} />
      </label>
      {e && <div style={{ fontWeight: 600 }}>Dann steht das Fach bei Ø {komma(e.gesamt)} – Zeugnis: {p.system === 'PUNKTE' ? `${zeugnis(e)} Punkte` : `Note ${zeugnis(e)}`}</div>}
    </Dialog>
  );
}

function ZeugnisDialog(p: { system: NotenSystem; faecher: FachSchnitt[]; zeitraum: string; onZu: () => void }) {
  const { anzeige } = useFach();
  const schnitt = p.faecher.length ? p.faecher.map(zeugnis).reduce((a, b) => a + b, 0) / p.faecher.length : null;
  return (
    <Dialog titel="Zeugnis-Vorschau" onZu={p.onZu} knoepfe={<button class="textknopf" onClick={p.onZu}>Schließen</button>}>
      <div class="klein">{p.zeitraum}</div>
      {p.faecher.map((f) => (
        <div key={f.fach} class="tabelle-zeile"><span>{anzeige(f.fach)}</span><b>{p.system === 'PUNKTE' ? `${zeugnis(f)} P` : zeugnis(f)}</b></div>
      ))}
      {schnitt !== null && (
        <div style={{ fontWeight: 600 }}>
          Durchschnitt der Zeugniswerte: {komma(schnitt)}{p.system === 'PUNKTE' ? ` P (≈ Note ${komma(alsSchulnote(schnitt))})` : ''}
        </div>
      )}
      <div class="sehr-klein">Nur eine Vorschau aus deinen eingetragenen Noten – die Lehrkraft entscheidet.</div>
    </Dialog>
  );
}

function AbiDialog(p: { schnitt: number | null; onZu: () => void }) {
  const start = p.schnitt !== null ? String(Math.min(15, Math.max(0, Math.floor(p.schnitt + 0.5)))) : '10';
  const [pr, setPr] = useState<string[]>(Array(5).fill(start));
  const punkte = pr.map((t) => (/^\d{1,2}$/.test(t) && parseInt(t, 10) <= 15 ? parseInt(t, 10) : null));
  const blockI = p.schnitt !== null ? Abi.blockI(p.schnitt) : null;
  const blockII = punkte.every((x) => x !== null) ? Abi.blockII(punkte as number[]) : null;
  const gesamt = blockI !== null && blockII !== null ? blockI + blockII : null;
  const note = gesamt !== null ? Abi.note(gesamt) : null;
  const geschafft = blockI !== null && blockII !== null && Abi.bestanden(blockI, blockII);
  return (
    <Dialog titel="Abi-Rechner" onZu={p.onZu} knoepfe={<button class="textknopf" onClick={p.onZu}>Schließen</button>}>
      <div>
        {p.schnitt === null
          ? 'Trag zuerst Noten ein – daraus wird Block I geschätzt.'
          : `Block I aus deinem Durchschnitt aller Halbjahre (Ø ${komma(p.schnitt)} P). Trag unten ein, wie viele Punkte du in den fünf Prüfungen erwartest.`}
      </div>
      <div class="zeile">
        {pr.map((t, i) => (
          <label key={i} class="feld fuell">
            P{i + 1}
            <input type="text" inputMode="numeric" class={punkte[i] === null ? 'fehler' : ''} value={t} onInput={(e) => setPr(pr.map((x, k) => (k === i ? (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 2) : x)))} />
          </label>
        ))}
      </div>
      {blockI !== null && <div>Block I: {blockI} von {Abi.BLOCK_I_MAX}</div>}
      {blockII !== null && <div>Block II: {blockII} von {Abi.BLOCK_II_MAX}</div>}
      {gesamt !== null && (
        <>
          <div style={{ fontSize: 17, fontWeight: 700 }}>Gesamt: {gesamt} von 900{note !== null && geschafft ? ` · Note ≈ ${komma(note)}` : ''}</div>
          <div style={{ color: geschafft ? 'var(--primary)' : 'var(--error)' }}>
            {geschafft ? 'Bestehens-Grenzen erreicht.' : `Unter den Mindestwerten (Block I ${Abi.BLOCK_I_MIN}, Block II ${Abi.BLOCK_II_MIN}, gesamt ${Abi.GESAMT_MIN}).`}
          </div>
        </>
      )}
      <div class="sehr-klein">Block I = Durchschnitt × 40 (Summe der Punkte geteilt durch die Zahl der gewerteten Ergebnisse × 40). Laut Oberstufenverordnung zählen 36 bis 40 Kurshalbjahre, einige davon doppelt – diese Auswahl macht deine Schule. Das hier ist darum nur eine Schätzung.</div>
    </Dialog>
  );
}

function FachKarte(p: { system: NotenSystem; schnitt: FachSchnitt; noten: Note[]; anteil: number; vorher: FachSchnitt | undefined; ziel: number | undefined; onZiel: (z: number) => void; onNote: (n: Note) => void }) {
  const { farbe, anzeige } = useFach();
  const [prognoseOffen, setPrognoseOffen] = useState(false);
  const { system, schnitt: s } = p;
  const { min, max, hoeherIstBesser } = SYSTEME[system];
  const z = zeugnis(s);
  // Ohne gespeichertes Ziel: einen Schritt besser als der aktuelle Zeugnisstand.
  const zielWert = p.ziel !== undefined && p.ziel >= min && p.ziel <= max ? p.ziel : Math.min(max, Math.max(min, hoeherIstBesser ? z + 1 : z - 1));
  const diff = p.vorher ? s.gesamt - p.vorher.gesamt : 0;
  const besser = p.vorher !== undefined && (hoeherIstBesser ? diff > 0.05 : diff < -0.05);
  const schlechter = p.vorher !== undefined && (hoeherIstBesser ? diff < -0.05 : diff > 0.05);
  return (
    <div class="karte zeile" style={{ padding: 0, overflow: 'hidden', alignItems: 'stretch' }}>
      <div style={{ width: 5, background: farbe(s.fach) }} />
      <div class="fuell stapel" style={{ padding: '12px 8px 8px 14px', gap: 4 }}>
        <div class="zeile">
          <div class="fuell">
            <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--on-surface)' }}>{anzeige(s.fach)}</div>
            <div class="sehr-klein">{[s.klausur !== null ? `Klausuren Ø ${komma(s.klausur)}` : null, s.muendlich !== null ? `Mündlich Ø ${komma(s.muendlich)}` : null].filter(Boolean).join(' · ')}</div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 28, fontWeight: 700, color: farbe(s.fach) }}>{z}</div>
            <div class="sehr-klein">Ø {komma(s.gesamt)}</div>
          </div>
        </div>
        {p.vorher && (
          <div class="sehr-klein" style={{ color: besser ? 'var(--primary)' : schlechter ? 'var(--error)' : undefined }}>
            Vorhalbjahr Ø {komma(p.vorher.gesamt)} · {besser ? `▲ ${komma(Math.abs(diff))} besser` : schlechter ? `▼ ${komma(Math.abs(diff))} schlechter` : 'gleich geblieben'}
          </div>
        )}
        {p.noten.map((n) => (
          <div key={n.id} class="zeile">
            <button class="textknopf fuell" style={{ textAlign: 'left', color: 'var(--on-surface)', fontWeight: 400 }} onClick={() => p.onNote(n)}>
              {wertText(system, n.punkte)} · {notenArtText(n.art)} · {deutschesDatum(n.datum)}{n.notiz.trim() ? ` · ${n.notiz}` : ''}
            </button>
            <button class="icon-knopf" aria-label="Löschen" onClick={() => notenLoeschen(n.id)}><Icon name="muell" /></button>
          </div>
        ))}
        <div class="zeile">
          <b>Ziel</b>
          <button class="textknopf" disabled={zielWert <= min} onClick={() => p.onZiel(zielWert - 1)}>−</button>
          <b>{wertText(system, zielWert)}</b>
          <button class="textknopf" disabled={zielWert >= max} onClick={() => p.onZiel(zielWert + 1)}>+</button>
          {zielErreicht(z, zielWert, system) && <span class="sehr-klein" style={{ color: 'var(--primary)' }}>✓ erreicht</span>}
        </div>
        {ARTEN.map((art) => {
          const b = benoetigt(p.noten, art, zielWert, p.anteil, system);
          return (
            <div key={art} class="sehr-klein">
              Nächste {art === 'KLAUSUR' ? 'Klausur' : 'mündliche Note'}:{' '}
              {b.art === 'PUNKTE' ? (system === 'PUNKTE' ? `mindestens ${b.punkte} P` : `höchstens Note ${b.punkte}`) : b.art === 'SICHER' ? 'Ziel schon sicher' : 'reicht allein nicht'}
            </div>
          );
        })}
        <button class="textknopf" style={{ alignSelf: 'flex-start' }} onClick={() => setPrognoseOffen(true)}>Was wäre wenn …</button>
      </div>
      {prognoseOffen && <PrognoseDialog system={system} fach={s.fach} noten={p.noten} anteil={p.anteil} onZu={() => setPrognoseOffen(false)} />}
    </div>
  );
}

export function Noten(p: { system: NotenSystem; pruefungen: Pruefung[]; fachVorschlaege: string[] }) {
  const alleNoten = useAblage(notenAblage);
  const anteile = useAblage(notenAnteilAblage);
  const ziele = useAblage(notenZieleAblage);
  const heute = useHeute();
  const jetzt = useJetzt();
  const { system } = p;
  const noten = alleNoten.filter((n) => n.system === system);
  const anteil = anteile[system];
  const [entwurf, setEntwurf] = useState<Entwurf | null>(null);
  const [zeugnisOffen, setZeugnisOffen] = useState(false);
  const [abiOffen, setAbiOffen] = useState(false);
  // Gibt es im aktuellen Halbjahr noch keine Noten, aber in einem früheren, starten wir dort. "" = alle Halbjahre.
  const [halbjahr, setHalbjahr] = useState(() => {
    const aktuell = halbjahrVon(heute);
    if (noten.length === 0 || noten.some((n) => halbjahrVon(n.datum) === aktuell)) return aktuell;
    return halbjahrVon([...noten].sort((a, b) => b.datum.localeCompare(a.datum))[0].datum);
  });
  const halbjahre = [...new Set([...noten.map((n) => halbjahrVon(n.datum)), halbjahrVon(heute)])].sort().reverse();
  const sichtbar = halbjahr === '' ? noten : noten.filter((n) => halbjahrVon(n.datum) === halbjahr);
  const faecher = nachFach(sichtbar, anteil);
  const gesamt = gesamtSchnitt(faecher);
  const davor = halbjahrDavor(halbjahr);
  const vorher = new Map(davor ? nachFach(noten.filter((n) => halbjahrVon(n.datum) === davor), anteil).map((f) => [fachSchluessel(f.fach), f]) : []);
  // Geschriebene Klausuren, zu denen noch keine Note eingetragen ist.
  const offene = p.pruefungen
    .filter((x) => x.art === 'KLAUSUR' && pruefungIstVorbei(x, heute, jetzt) && x.fach.trim() !== '')
    .filter((x) => !noten.some((n) => n.art === 'KLAUSUR' && n.datum === x.datum && fachSchluessel(n.fach) === fachSchluessel(x.fach)));

  return (
    <div class="seite">
      <div class="karte" style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)', padding: 16 }}>
        <div class="titel-klein" style={{ color: 'inherit' }}>Gesamtdurchschnitt</div>
        {gesamt === null ? <div class="gross-zahl">–</div> : (
          <>
            <div class="gross-zahl">{system === 'PUNKTE' ? `${komma(gesamt)} Punkte` : `Note ${komma(gesamt)}`}</div>
            <div>{system === 'PUNKTE' ? `≈ Note ${komma(alsSchulnote(gesamt))} · ` : ''}{faecher.length} {faecher.length === 1 ? 'Fach' : 'Fächer'}</div>
          </>
        )}
      </div>
      <div class="scroll-x">
        <Chip an={halbjahr === ''} onClick={() => setHalbjahr('')}>Alle</Chip>
        {halbjahre.map((h) => <Chip key={h} an={halbjahr === h} onClick={() => setHalbjahr(h)}>{h}</Chip>)}
      </div>
      <AnteilRegler key={system} anteil={anteil} onAnteil={(a) => notenAnteilAblage.set((x) => ({ ...x, [system]: a }))} />
      {system === 'NOTEN' && <LeerHinweis text="Klasse 5–10: Klassenarbeiten zählen laut älterem Erlass etwa 25–40 % der Note (eine 45-Minuten-Arbeit ca. 20 %). Frag im Zweifel deine Lehrkraft." />}
      {faecher.length > 0 && (
        <div class="scroll-x">
          <Chip onClick={() => setZeugnisOffen(true)}>Zeugnis-Vorschau</Chip>
          {system === 'PUNKTE' && <Chip onClick={() => setAbiOffen(true)}>Abi-Rechner</Chip>}
        </div>
      )}
      {offene.length > 0 && (
        <>
          <div class="titel-klein" style={{ color: 'var(--on-surface-variant)' }}>Klausur geschrieben – Note eintragen?</div>
          <div class="wrap">
            {offene.map((x) => <Chip key={x.id} onClick={() => setEntwurf({ fach: x.fach, art: 'KLAUSUR', datum: x.datum })}>{x.fach} · {ganzKurzesDatum(x.datum)}</Chip>)}
          </div>
        </>
      )}
      {noten.length === 0 ? (
        <LeerHinweis text={system === 'PUNKTE' ? 'Noch keine Noten. Trag sie unten rechts ein (0 bis 15 Punkte) – die App rechnet Durchschnitt und Zeugnispunkte aus. Alles bleibt nur auf deinem Gerät.' : 'Noch keine Noten. Trag sie unten rechts ein (1 bis 6) – die App rechnet Durchschnitt und Zeugnisnote aus. Alles bleibt nur auf deinem Gerät.'} />
      ) : faecher.length === 0 ? (
        <LeerHinweis text="In diesem Halbjahr gibt es noch keine Noten." />
      ) : null}
      {faecher.map((f) => (
        <FachKarte
          key={fachSchluessel(f.fach)}
          system={system}
          schnitt={f}
          noten={sichtbar.filter((n) => fachSchluessel(n.fach) === fachSchluessel(f.fach))}
          anteil={anteil}
          vorher={vorher.get(fachSchluessel(f.fach))}
          ziel={ziele[`${system}|${fachSchluessel(f.fach)}`]}
          onZiel={(z) => notenZieleAblage.set((x) => ({ ...x, [`${system}|${fachSchluessel(f.fach)}`]: z }))}
          onNote={(n) => setEntwurf({ bestehend: n, art: n.art, datum: n.datum })}
        />
      ))}
      <button class="fab" onClick={() => setEntwurf({ art: 'MUENDLICH', datum: heute })}><Icon name="plus" />Note</button>
      {zeugnisOffen && <ZeugnisDialog system={system} faecher={faecher} zeitraum={halbjahr === '' ? 'Alle Halbjahre' : halbjahr} onZu={() => setZeugnisOffen(false)} />}
      {abiOffen && <AbiDialog schnitt={durchschnittAllerHalbjahre(noten, anteil)} onZu={() => setAbiOffen(false)} />}
      {entwurf && (
        <NoteDialog
          system={system}
          entwurf={entwurf}
          vorschlaege={p.fachVorschlaege}
          onZu={() => setEntwurf(null)}
          onSpeichern={(fach, punkte, art, datum, notiz) => {
            if (entwurf.bestehend) notenBearbeiten(entwurf.bestehend.id, { fach: fach.trim(), punkte, art, datum, notiz: notiz.trim() });
            else notenHinzufuegen({ system, fach: fach.trim(), punkte, art, datum, notiz: notiz.trim() });
            setEntwurf(null);
          }}
        />
      )}
    </div>
  );
}

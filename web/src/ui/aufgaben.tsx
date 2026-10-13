import { useEffect, useState } from 'preact/hooks';
import { type Hausaufgabe, type Pruefung, type PruefungsArt, istUeberfaellig, pruefungIstVorbei, pruefungsArtText, tageBis } from '../logic/aufgaben';
import { FachName } from '../logic/fachname';
import { abschnitte } from '../logic/tabellentext';
import { type Datum, deutschesDatum, heute as heuteTag, langesDatum, plusTage } from '../logic/zeit';
import { aufgabenAblage, hausaufgabeBearbeiten, hausaufgabeHinzufuegen, hausaufgabeLoeschen, hausaufgabeUmschalten, pruefungBearbeiten, pruefungHinzufuegen, pruefungLoeschen, pruefungenAblage } from '../services/daten';
import { fotoIds, fotoLesen, fotoLoeschen, fotoSpeichern, fotoVerkleinern } from '../services/kv';
import { diktatMoeglich, diktieren } from '../services/diktat';
import { claudeLoesen } from '../services/claude';
import { textAusFoto } from '../services/ocr';
import { useAblage } from '../services/speicher';
import { Badge, Chip, Dialog, Karte, LeerHinweis, Ueberschrift, Icon, useFach, useHeute, useJetzt } from './basis';

function faelligkeitText(d: Datum, heute: Datum): string {
  if (d === heute) return 'heute fällig';
  if (d === plusTage(heute, 1)) return 'morgen fällig';
  if (d < heute) return `war am ${deutschesDatum(d)} fällig`;
  return `bis ${deutschesDatum(d)}`;
}

/** Eingabe für ein Datum (Browser-Datumsauswahl). */
function DatumsFeld(p: { wert: Datum | null; onAendern: (d: Datum | null) => void }) {
  return <input type="date" value={p.wert ?? ''} onInput={(e) => p.onAendern((e.target as HTMLInputElement).value || null)} />;
}

/** Was beim Speichern einer Hausaufgabe mit dem Foto passiert. */
export type FotoAenderung = { art: 'KEINE' } | { art: 'NEU'; bild: Blob } | { art: 'ENTFERNEN' };

/** Foto nach dem Speichern der Aufgabe [id] anwenden (verkleinern, ablegen bzw. löschen). */
export async function fotoAnwenden(id: string, f: FotoAenderung): Promise<void> {
  if (f.art === 'ENTFERNEN') await fotoLoeschen(id);
  else if (f.art === 'NEU') {
    const klein = await fotoVerkleinern(f.bild);
    if (klein) await fotoSpeichern(id, klein);
  }
}

/** Ids der Aufgaben mit Foto – für das 📷 in der Liste. */
function useFotoIds(version: number): Set<string> {
  const [ids, setIds] = useState<Set<string>>(new Set());
  useEffect(() => {
    let aktiv = true;
    void fotoIds().then((l) => aktiv && setIds(new Set(l)));
    return () => {
      aktiv = false;
    };
  }, [version]);
  return ids;
}

/** Alle Tafelfotos, nach Fach gruppiert; ein Tipp öffnet das Foto groß. */
function FotoGalerie(p: { aufgaben: Hausaufgabe[]; onZu: () => void }) {
  const { anzeige } = useFach();
  const [gross, setGross] = useState<string | null>(null);
  const [urls, setUrls] = useState<Record<string, string>>({});
  useEffect(() => {
    let aktiv = true;
    const neu: string[] = [];
    void (async () => {
      const m: Record<string, string> = {};
      for (const a of p.aufgaben) {
        const b = await fotoLesen(a.id);
        if (b) {
          const u = URL.createObjectURL(b);
          neu.push(u);
          m[a.id] = u;
        }
      }
      if (aktiv) setUrls(m);
    })();
    return () => {
      aktiv = false;
      neu.forEach((u) => URL.revokeObjectURL(u));
    };
  }, [p.aufgaben.map((a) => a.id).join(',')]);
  const gruppen = new Map<string, Hausaufgabe[]>();
  for (const a of p.aufgaben) {
    const k = FachName.schluessel(a.fach);
    gruppen.set(k, [...(gruppen.get(k) ?? []), a]);
  }
  return (
    <>
      <Dialog titel="Tafelfotos" onZu={p.onZu} knoepfe={<button class="textknopf" onClick={p.onZu}>Schließen</button>}>
        {[...gruppen.values()].map((liste) => (
          <div key={liste[0].id} class="stapel" style={{ gap: 6 }}>
            <div style={{ fontWeight: 600 }}>{anzeige(liste[0].fach.trim() === '' ? 'Ohne Fach' : liste[0].fach)}</div>
            <div class="scroll-x">
              {[...liste].sort((a, b) => b.erstelltAm - a.erstelltAm).map((a) => (
                <button key={a.id} class="icon-knopf" aria-label={`Foto: ${a.text}`} style={{ width: 96, height: 96, padding: 0, borderRadius: 10, overflow: 'hidden', flex: 'none' }} onClick={() => setGross(a.id)}>
                  {urls[a.id] && <img src={urls[a.id]} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
                </button>
              ))}
            </div>
          </div>
        ))}
      </Dialog>
      {gross && <FotoAnsicht id={gross} fach={p.aufgaben.find((a) => a.id === gross)?.fach ?? ''} text={p.aufgaben.find((a) => a.id === gross)?.text ?? ''} onZu={() => setGross(null)} />}
    </>
  );
}

function FotoAnsicht(p: { id: string; fach: string; text: string; onZu: () => void }) {
  const [url, setUrl] = useState<string | null>(null);
  const [hinweis, setHinweis] = useState('');
  useEffect(() => {
    let u: string | null = null;
    void fotoLesen(p.id).then((b) => {
      if (b) {
        u = URL.createObjectURL(b);
        setUrl(u);
      }
    });
    return () => {
      if (u) URL.revokeObjectURL(u);
    };
  }, [p.id]);
  return (
    <Dialog
      titel="Foto"
      onZu={p.onZu}
      knoepfe={
        <>
          <button class="textknopf" onClick={async () => setHinweis(await claudeLoesen(p.id, p.fach, p.text))}>🤖 Mit Claude lösen</button>
          <button class="textknopf" onClick={p.onZu}>Schließen</button>
        </>
      }
    >
      {url ? <img src={url} alt="Foto zur Hausaufgabe" style={{ width: '100%', borderRadius: 8 }} /> : <div>Kein Foto gefunden.</div>}
      {hinweis && <div class="sehr-klein">{hinweis}</div>}
    </Dialog>
  );
}

export function HausaufgabeDialog(p: {
  bestehend?: Hausaufgabe | null;
  vorschlagFach?: string;
  naechsteStunde?: Datum | null;
  onZu: () => void;
  hatFoto?: boolean;
  onSpeichern: (fach: string, text: string, faellig: Datum | null, foto: FotoAenderung) => void;
}) {
  const heute = useHeute();
  const [neuesFoto, setNeuesFoto] = useState<Blob | null>(null);
  const [fotoWeg, setFotoWeg] = useState(false);
  const [status, setStatus] = useState('');
  const [hoert, setHoert] = useState(false);
  const [fach, setFach] = useState(p.bestehend?.fach ?? p.vorschlagFach ?? '');
  const [text, setText] = useState(p.bestehend?.text ?? '');
  const [faellig, setFaellig] = useState<Datum | null>(p.bestehend ? p.bestehend.faellig : plusTage(heute, 1));
  const [selbst, setSelbst] = useState(p.bestehend != null);
  // Ist die nächste Stunde ermittelt, wird sie übernommen, solange man selbst noch kein Datum gewählt hat.
  const wirksam = p.naechsteStunde && !selbst ? p.naechsteStunde : faellig;
  const setze = (d: Datum | null) => {
    setFaellig(d);
    setSelbst(true);
  };
  return (
    <Dialog
      titel={p.bestehend ? 'Hausaufgabe bearbeiten' : 'Neue Hausaufgabe'}
      onZu={p.onZu}
      knoepfe={
        <>
          <button class="textknopf" onClick={p.onZu}>Abbrechen</button>
          <button class="textknopf" disabled={text.trim() === ''} onClick={() => p.onSpeichern(fach, text, wirksam, neuesFoto ? { art: 'NEU', bild: neuesFoto } : fotoWeg ? { art: 'ENTFERNEN' } : { art: 'KEINE' })}>Speichern</button>
        </>
      }
    >
      <label class="feld">Fach<input type="text" value={fach} onInput={(e) => setFach((e.target as HTMLInputElement).value)} /></label>
      <label class="feld">Was ist zu tun?<textarea rows={3} value={text} onInput={(e) => setText((e.target as HTMLTextAreaElement).value)} /></label>
      <div class="sehr-klein">Fällig</div>
      <div class="wrap">
        {p.naechsteStunde ? <Chip onClick={() => setze(p.naechsteStunde!)}>Nächste Stunde</Chip> : <Chip onClick={() => setze(heute)}>Heute</Chip>}
        <Chip onClick={() => setze(plusTage(heute, 1))}>Morgen</Chip>
        <Chip onClick={() => setze(null)}>Ohne</Chip>
      </div>
      <DatumsFeld wert={wirksam} onAendern={setze} />
      <div class="zeile wrap">
        {diktatMoeglich() && (
          <button
            class="knopf"
            disabled={hoert}
            onClick={() => {
              setHoert(true);
              setStatus('Ich höre zu …');
              diktieren((t) => {
                setHoert(false);
                if (t) {
                  setText((alt) => (alt.trim() === '' ? t : `${alt.trimEnd()} ${t}`));
                  setStatus('');
                } else setStatus('Nichts verstanden oder Mikrofon nicht erlaubt.');
              });
            }}
          >
            🎤 Diktieren
          </button>
        )}
        {(neuesFoto || (p.hatFoto && !fotoWeg)) && (
          <button
            class="knopf"
            onClick={async () => {
              setStatus('Text wird gelesen … (beim ersten Mal lädt dafür ein Sprachmodell, das kann dauern)');
              const bild = neuesFoto ?? (p.bestehend ? await fotoLesen(p.bestehend.id) : null);
              const t = bild ? await textAusFoto(bild) : null;
              if (t) {
                setText((alt) => (alt.trim() === '' ? t : `${alt.trimEnd()}\n${t}`));
                setStatus('Text übernommen – bitte prüfen.');
              } else setStatus('Kein Text erkannt (oder keine Verbindung für das Sprachmodell).');
            }}
          >
            🔍 Text aus Foto
          </button>
        )}
      </div>
      {status && <div class="sehr-klein">{status}</div>}
      <label class="knopf breit" style={{ textAlign: 'center', cursor: 'pointer' }}>
        {neuesFoto ? '📷 Neues Foto bereit' : p.hatFoto && !fotoWeg ? '📷 Foto ersetzen' : '📷 Foto aufnehmen oder aus Galerie wählen'}
        <input
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={(e) => {
            const f = (e.target as HTMLInputElement).files?.[0];
            if (f) {
              setNeuesFoto(f);
              setFotoWeg(false);
            }
          }}
        />
      </label>
      {(neuesFoto || (p.hatFoto && !fotoWeg)) && (
        <button class="textknopf" onClick={() => { setNeuesFoto(null); setFotoWeg(true); }}>Foto entfernen</button>
      )}
      <div class="sehr-klein">
        {wirksam ? `Gewählt: ${langesDatum(wirksam)}${wirksam === p.naechsteStunde ? ' (nächste Stunde)' : ''}` : 'Ohne Datum'}
      </div>
    </Dialog>
  );
}

function AufgabenZeile(p: { h: Hausaufgabe; heute: Datum; hatFoto: boolean; onBearbeiten: () => void; onFoto: () => void }) {
  const { farbe, anzeige } = useFach();
  const { h } = p;
  const ueber = istUeberfaellig(h, p.heute);
  return (
    <div class="karte zeile" style={{ padding: '6px 6px 6px 10px' }}>
      <input type="checkbox" checked={h.erledigt} onChange={() => hausaufgabeUmschalten(h.id)} style={{ width: 22, height: 22, accentColor: 'var(--primary)' }} />
      <div class="fuell" style={{ padding: '4px 0', cursor: 'pointer' }} onClick={p.onBearbeiten}>
        {h.fach.trim() !== '' && (
          <div class="zeile" style={{ gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: farbe(h.fach) }} />
            <span class="sehr-klein">{anzeige(h.fach)}</span>
          </div>
        )}
        <div class={h.erledigt ? 'durch' : ''} style={{ fontSize: 16, color: h.erledigt ? 'var(--on-surface-variant)' : 'var(--on-surface)', whiteSpace: 'pre-wrap' }}>
          {abschnitte(h.text).map((a, i) =>
            a.art === 'NORMAL' ? (
              <div key={i}>{a.text}</div>
            ) : (
              <table key={i} class="aufgaben-tabelle">
                <tbody>
                  {a.zeilen.map((z, r) => (
                    <tr key={r}>
                      {z.map((c, k) => (r === 0 ? <th key={k}>{c}</th> : <td key={k}>{c}</td>))}
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          )}
        </div>
        {h.faellig && (
          <div class="sehr-klein" style={{ fontWeight: ueber ? 700 : 400, color: ueber ? 'var(--error)' : undefined }}>{faelligkeitText(h.faellig, p.heute)}</div>
        )}
      </div>
      {p.hatFoto && <button class="icon-knopf" aria-label="Foto ansehen" onClick={p.onFoto}>📷</button>}
      <button class="icon-knopf" aria-label="Löschen" onClick={() => hausaufgabeLoeschen(h.id)}>
        <Icon name="muell" />
      </button>
    </div>
  );
}

export function Aufgaben() {
  const aufgaben = useAblage(aufgabenAblage);
  const heute = useHeute();
  const [dialog, setDialog] = useState<'neu' | Hausaufgabe | null>(null);
  const [fotoVersion, setFotoVersion] = useState(0);
  const [fotoOffen, setFotoOffen] = useState<string | null>(null);
  const fotos = useFotoIds(fotoVersion);
  const [galerie, setGalerie] = useState(false);
  const offen = aufgaben.filter((h) => !h.erledigt);
  const erledigt = aufgaben.filter((h) => h.erledigt);
  const ueber = offen.filter((h) => istUeberfaellig(h, heute)).length;
  return (
    <div class="seite">
      <Ueberschrift titel={offen.length === 0 ? 'Nichts offen' : `${offen.length} offen`} unter={ueber > 0 ? `${ueber} überfällig` : null} />
      {aufgaben.some((h) => fotos.has(h.id)) && (
        <button class="textknopf" style={{ alignSelf: 'flex-start' }} onClick={() => setGalerie(true)}>
          📷 Tafelfotos ({aufgaben.filter((h) => fotos.has(h.id)).length})
        </button>
      )}
      {aufgaben.length > 0 && (
        <div>
          <div class="fortschritt"><div style={{ width: `${(erledigt.length / aufgaben.length) * 100}%` }} /></div>
          <div class="sehr-klein">{erledigt.length} von {aufgaben.length} erledigt</div>
        </div>
      )}
      {aufgaben.length === 0 && <LeerHinweis text="Noch keine Hausaufgaben eingetragen. Unten rechts hinzufügen." />}
      {offen.map((h) => <AufgabenZeile key={h.id} h={h} heute={heute} hatFoto={fotos.has(h.id)} onBearbeiten={() => setDialog(h)} onFoto={() => setFotoOffen(h.id)} />)}
      {erledigt.length > 0 && (
        <>
          <div style={{ paddingTop: 12 }}>
            <div class="titel-klein">Erledigt</div>
            <div class="sehr-klein">Verschwindet beim nächsten Start der App.</div>
          </div>
          {erledigt.map((h) => <AufgabenZeile key={h.id} h={h} heute={heute} hatFoto={fotos.has(h.id)} onBearbeiten={() => setDialog(h)} onFoto={() => setFotoOffen(h.id)} />)}
        </>
      )}
      <button class="fab" onClick={() => setDialog('neu')}><Icon name="plus" />Aufgabe</button>
      {dialog && (
        <HausaufgabeDialog
          bestehend={dialog === 'neu' ? null : dialog}
          hatFoto={dialog !== 'neu' && fotos.has(dialog.id)}
          onZu={() => setDialog(null)}
          onSpeichern={(fach, text, faellig, foto) => {
            let id: string;
            if (dialog === 'neu') id = hausaufgabeHinzufuegen(fach, text, faellig);
            else {
              id = dialog.id;
              hausaufgabeBearbeiten(id, fach, text, faellig);
            }
            setDialog(null);
            void fotoAnwenden(id, foto).then(() => setFotoVersion((n) => n + 1));
          }}
        />
      )}
      {fotoOffen && <FotoAnsicht id={fotoOffen} fach={aufgaben.find((a) => a.id === fotoOffen)?.fach ?? ''} text={aufgaben.find((a) => a.id === fotoOffen)?.text ?? ''} onZu={() => setFotoOffen(null)} />}
      {galerie && <FotoGalerie aufgaben={aufgaben.filter((h) => fotos.has(h.id))} onZu={() => setGalerie(false)} />}
    </div>
  );
}

// ---------- Klausuren ----------

function PruefungDialog(p: { bestehend?: Pruefung | null; onZu: () => void; onSpeichern: (t: { fach: string; titel: string; datum: Datum; art: PruefungsArt; notiz: string }) => void }) {
  const [art, setArt] = useState<PruefungsArt>(p.bestehend?.art ?? 'KLAUSUR');
  const [fach, setFach] = useState(p.bestehend?.fach ?? '');
  const [titel, setTitel] = useState(p.bestehend?.titel ?? '');
  const [notiz, setNotiz] = useState(p.bestehend?.notiz ?? '');
  const [datum, setDatum] = useState<Datum>(p.bestehend?.datum ?? plusTage(heuteTag(), 7));
  return (
    <Dialog
      titel={p.bestehend ? 'Termin bearbeiten' : 'Test oder Klausur'}
      onZu={p.onZu}
      knoepfe={
        <>
          <button class="textknopf" onClick={p.onZu}>Abbrechen</button>
          <button class="textknopf" disabled={fach.trim() === '' && titel.trim() === ''} onClick={() => p.onSpeichern({ fach, titel, datum, art, notiz })}>Speichern</button>
        </>
      }
    >
      <div class="zeile">
        {(['KLAUSUR', 'TEST'] as PruefungsArt[]).map((a) => <Chip key={a} an={art === a} onClick={() => setArt(a)}>{pruefungsArtText(a)}</Chip>)}
      </div>
      <label class="feld">Fach<input type="text" value={fach} onInput={(e) => setFach((e.target as HTMLInputElement).value)} /></label>
      <label class="feld">Thema (optional)<input type="text" value={titel} onInput={(e) => setTitel((e.target as HTMLInputElement).value)} /></label>
      <label class="feld">Notiz (optional)<input type="text" value={notiz} onInput={(e) => setNotiz((e.target as HTMLInputElement).value)} /></label>
      <label class="feld">Datum<input type="date" value={datum} onInput={(e) => setDatum((e.target as HTMLInputElement).value || datum)} /></label>
    </Dialog>
  );
}

function PruefungsKarte(p: { pr: Pruefung; heute: Datum; jetzt: number; onBearbeiten: () => void }) {
  const { farbe, anzeige } = useFach();
  const { pr } = p;
  const tage = tageBis(pr, p.heute);
  const vorbei = pruefungIstVorbei(pr, p.heute, p.jetzt);
  const dringend = !vorbei && tage <= 3;
  const akzent = vorbei ? 'var(--outline)' : dringend ? 'var(--error)' : farbe(pr.fach);
  return (
    <div class="karte zeile" style={{ padding: 0, overflow: 'hidden', opacity: vorbei ? 0.7 : 1, cursor: 'pointer' }} onClick={p.onBearbeiten}>
      <div style={{ width: 5, alignSelf: 'stretch', background: akzent }} />
      <div class="fuell" style={{ padding: '12px 4px 12px 14px' }}>
        <div class="zeile">
          <Badge farbe={akzent} text="var(--surface)">{pruefungsArtText(pr.art)}</Badge>
          <span style={{ fontSize: 16, fontWeight: 700, color: 'var(--on-surface)' }}>{anzeige(pr.fach)}</span>
        </div>
        {pr.titel.trim() !== '' && <div style={{ color: 'var(--on-surface)' }}>{pr.titel}</div>}
        <div class="sehr-klein">{langesDatum(pr.datum)}</div>
        {pr.notiz.trim() !== '' && <div class="sehr-klein">{pr.notiz}</div>}
      </div>
      <div style={{ textAlign: 'right', paddingRight: 4 }}>
        <div style={{ fontWeight: 600, color: dringend ? 'var(--error)' : 'var(--on-surface-variant)' }}>
          {vorbei ? 'vorbei' : tage === 0 ? 'heute' : tage === 1 ? 'morgen' : `in ${tage} T.`}
        </div>
        <button class="icon-knopf" aria-label="Löschen" onClick={(e) => { e.stopPropagation(); pruefungLoeschen(pr.id); }}>
          <Icon name="muell" />
        </button>
      </div>
    </div>
  );
}

export function Klausuren() {
  const liste = useAblage(pruefungenAblage);
  const heute = useHeute();
  const jetzt = useJetzt();
  const [dialog, setDialog] = useState<'neu' | Pruefung | null>(null);
  const kommend = liste.filter((p) => !pruefungIstVorbei(p, heute, jetzt));
  const vorbei = liste.filter((p) => pruefungIstVorbei(p, heute, jetzt));
  const n = kommend[0];
  const tage = n ? tageBis(n, heute) : 0;
  const unter = n ? (tage <= 0 ? `Heute: ${n.fach}` : tage === 1 ? `Morgen: ${n.fach}` : tage <= 7 ? `In ${tage} Tagen: ${n.fach}` : null) : null;
  return (
    <div class="seite">
      <Ueberschrift titel={kommend.length === 0 ? 'Nichts angesetzt' : kommend.length === 1 ? '1 Termin' : `${kommend.length} Termine`} unter={unter} />
      {liste.length === 0 && <LeerHinweis text="Noch keine Klausuren. Klausuren deiner Kurse erscheinen hier automatisch, sobald sie im Plan stehen. Eigene Tests kannst du unten rechts hinzufügen." />}
      {kommend.map((p) => <PruefungsKarte key={p.id} pr={p} heute={heute} jetzt={jetzt} onBearbeiten={() => setDialog(p)} />)}
      {vorbei.length > 0 && (
        <>
          <div style={{ paddingTop: 12 }}>
            <div class="titel-klein">Vorbei</div>
            <div class="sehr-klein">Wird eine Woche nach dem Termin automatisch entfernt.</div>
          </div>
          {vorbei.map((p) => <PruefungsKarte key={p.id} pr={p} heute={heute} jetzt={jetzt} onBearbeiten={() => setDialog(p)} />)}
        </>
      )}
      <button class="fab" onClick={() => setDialog('neu')}><Icon name="plus" />Termin</button>
      {dialog && (
        <PruefungDialog
          bestehend={dialog === 'neu' ? null : dialog}
          onZu={() => setDialog(null)}
          onSpeichern={(t) => {
            if (dialog === 'neu') pruefungHinzufuegen(t);
            else pruefungBearbeiten(dialog.id, t);
            setDialog(null);
          }}
        />
      )}
    </div>
  );
}

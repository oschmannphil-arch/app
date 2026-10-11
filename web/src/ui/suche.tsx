import { useEffect, useMemo, useState } from 'preact/hooks';
import { type Freiblock, freiblockStundenText } from '../logic/freizeit';
import { anzeigeName } from '../logic/hinweis';
import type { Lesson } from '../logic/models';
import { raumGruppe, schultagVersetzt, wochenReferenz } from '../logic/raster';
import {
  type Belegung,
  SchulTag,
  type Treffer,
  gleicherTreffer,
  lehrer as lehrerTreffer,
  raum as raumTreffer,
  steckbriefAus,
  trefferSchluessel
} from '../logic/suche';
import { type Datum, heute as heuteTag, kurzesDatum, langesDatum, plusTage, uhrzeitText, wochentagName } from '../logic/zeit';
import {
  favoritUmschalten,
  favoritenAblage,
  favoritenSortiert,
  verlaufAblage,
  verlaufEntfernen,
  verlaufLeeren,
  verlaufMerken
} from '../services/daten';
import { holePlan, holeWoche } from '../services/repo';
import { zugangAblage } from '../services/daten';
import { useAblage } from '../services/speicher';
import { Badge, Chip, Icon, Karte, Laden, useFach, useJetzt } from './basis';
import { FachName } from '../logic/fachname';
import { mitSymbol } from '../logic/symbole';

type TagStand = { art: 'LAEDT' } | { art: 'FEHLER'; text: string } | { art: 'OK'; tag: SchulTag; ausCache: boolean };

const uhr = (m: number | null) => (m === null ? 'später' : uhrzeitText(m));
const klasseUndFach = (l: Lesson) => [l.klasse, anzeigeName(l)].filter((x) => x && x.trim() !== '').join(' ');
const ort = (l: Lesson) => (l.raum.trim() !== '' ? `Raum ${l.raum}, ` : '') + klasseUndFach(l);

function statusText(tag: SchulTag, t: Treffer, istHeute: boolean, jetzt: number): string {
  // Gemerkte Favoriten können an diesem Tag fehlen – nicht als "frei" ausgeben, was gar nicht im Plan steht.
  if (tag.stundenVon(t).length === 0) return 'an diesem Tag nicht im Plan';
  if (t.art === 'L' && tag.faelltGanzAus(t)) return 'alle Stunden fallen aus';
  if (!istHeute) {
    const n = tag.stundenVon(t).filter((l) => !l.entfaellt).length;
    return n === 0 ? 'an diesem Tag kein Unterricht' : n === 1 ? '1 Stunde an diesem Tag' : `${n} Stunden an diesem Tag`;
  }
  const b: Belegung = tag.belegung(t, jetzt);
  if (b.art === 'JETZT') {
    const bis = b.lesson.ende !== null ? ` bis ${uhrzeitText(b.lesson.ende)}` : '';
    return t.art === 'L'
      ? `jetzt: ${ort(b.lesson)}${bis}`
      : `belegt: ${klasseUndFach(b.lesson)}${b.lesson.lehrer.trim() !== '' ? ` (${b.lesson.lehrer})` : ''}${bis}`;
  }
  const n = b.naechste;
  if (t.art === 'L') return n === null ? 'heute keine Stunde mehr' : `gerade keine Stunde · ab ${uhr(n.beginn)}: ${ort(n)}`;
  return n === null ? 'frei für den Rest des Tages' : `frei bis ${uhr(n.beginn)}`;
}

function TrefferSymbol({ t }: { t: Treffer }) {
  return <span style={{ fontSize: 22, width: 28, textAlign: 'center' }}>{t.art === 'L' ? '🧑‍🏫' : '🚪'}</span>;
}

function TrefferZeile(p: { tag: SchulTag; t: Treffer; istHeute: boolean; jetzt: number; onWahl: (t: Treffer) => void }) {
  return (
    <div class="karte zeile" style={{ cursor: 'pointer' }} onClick={() => p.onWahl(p.t)}>
      <TrefferSymbol t={p.t} />
      <div class="fuell">
        <div style={{ fontSize: 16, fontWeight: 500, color: 'var(--on-surface)' }}>{p.t.name}</div>
        <div class="sehr-klein">{statusText(p.tag, p.t, p.istHeute, p.jetzt)}</div>
      </div>
    </div>
  );
}

function BelegungsZeile({ l, t }: { l: Lesson; t: Treffer }) {
  const zusatz = t.art === 'L' ? (l.raum.trim() !== '' ? `Raum ${l.raum}` : null) : l.lehrer.trim() !== '' ? l.lehrer : null;
  const etikett = l.entfaellt ? 'entfällt' : l.istKlausur ? 'Klausur' : l.status === 'VERTRETUNG' ? 'Vertretung' : l.raumGeaendert ? 'Raumänderung' : null;
  const fehler = l.entfaellt || l.istKlausur;
  return (
    <div>
      <div class={l.entfaellt ? 'durch' : ''} style={{ color: l.entfaellt ? 'var(--on-surface-variant)' : 'var(--on-surface)' }}>
        {[l.klasse, anzeigeName(l) ? mitSymbol(anzeigeName(l)!) : null, zusatz].filter((x) => x && x.trim() !== '').join(' · ')}
      </div>
      {etikett && <Badge farbe={fehler ? 'var(--error)' : '#FF9800'} text={fehler ? 'var(--on-error)' : '#fff'}>{etikett}</Badge>}
    </div>
  );
}

function Detail(p: { tag: SchulTag; t: Treffer; istHeute: boolean; jetzt: number }) {
  const { tag, t } = p;
  const favoriten = useAblage(favoritenAblage);
  const istFavorit = favoriten.includes(trefferSchluessel(t));
  const [wocheOffen, setWocheOffen] = useState(false);
  const [naechste, setNaechste] = useState(false);
  const zeilen = useMemo(() => tag.tagesablauf(t), [tag, t]);
  const steckbrief = t.art === 'L' ? steckbriefAus(tag.stundenVon(t)) : null;
  return (
    <div class="seite">
      <div class="zeile" style={{ paddingTop: 4 }}>
        <TrefferSymbol t={t} />
        <div class="fuell">
          <div class="sehr-klein">{t.art === 'L' ? 'Lehrkraft' : 'Raum'}</div>
          <div style={{ fontSize: 24, fontWeight: 500, color: 'var(--on-surface)' }}>{t.name}</div>
        </div>
        <button class="icon-knopf" aria-label={istFavorit ? 'Aus Favoriten entfernen' : 'Zu Favoriten hinzufügen'} onClick={() => favoritUmschalten(t)} style={{ color: istFavorit ? 'var(--primary)' : 'var(--outline-variant)' }}>
          <Icon name="stern" />
        </button>
      </div>
      <div style={{ fontWeight: 600 }}>{statusText(tag, t, p.istHeute, p.jetzt)}</div>
      {t.art === 'L' && (
        <>
          {steckbrief && (
            <Karte>
              <div class="titel-klein">Steckbrief (an diesem Tag)</div>
              <div>Fächer: {steckbrief.faecher.map(mitSymbol).join(', ')}</div>
              <div>Jahrgänge: {steckbrief.jahrgaenge.join(', ')}</div>
              <div>Räume: {steckbrief.raeume.join(', ')}</div>
              <div>{steckbrief.stunden} {steckbrief.stunden === 1 ? 'Stunde' : 'Stunden'}</div>
            </Karte>
          )}
          <button class="knopf breit" onClick={() => setWocheOffen(!wocheOffen)}>
            {wocheOffen ? 'Zurück zum Tag' : `Wann ist ${t.name} diese Woche frei?`}
          </button>
        </>
      )}
      {wocheOffen && t.art === 'L' ? (
        <LehrerWoche t={t} naechste={naechste} onNaechste={setNaechste} />
      ) : (
        <>
          {zeilen.length === 0 && <div class="klein">An diesem Tag steht dazu nichts im Plan.</div>}
          {zeilen.map(([fenster, stunden]) => {
            const laeuft = p.istHeute && fenster.beginn !== null && fenster.ende !== null && p.jetzt >= fenster.beginn && p.jetzt < fenster.ende;
            return (
              <div key={fenster.stunde} class="karte zeile" style={{ alignItems: 'flex-start', background: laeuft ? 'var(--secondary-container)' : undefined, color: laeuft ? 'var(--on-secondary-container)' : undefined }}>
                <div style={{ minWidth: 76 }}>
                  <div style={{ fontWeight: 600, fontSize: 14, color: 'inherit' }}>{fenster.stunde}. Std</div>
                  {fenster.beginn !== null && fenster.ende !== null && <div class="sehr-klein" style={{ color: 'inherit' }}>{uhrzeitText(fenster.beginn)}–{uhrzeitText(fenster.ende)}</div>}
                </div>
                <div class="fuell stapel" style={{ gap: 4 }}>
                  {stunden.length === 0 && <div>{t.art === 'R' ? 'frei' : 'keine Stunde'}</div>}
                  {stunden.map((l, i) => <BelegungsZeile key={i} l={l} t={t} />)}
                </div>
              </div>
            );
          })}
        </>
      )}
      <div class="sehr-klein">Quelle: Schülerplan aller Klassen. Aufsichten, Sprechstunden und Räume ohne Unterricht stehen dort nicht – „frei“ heißt nur: laut Plan kein Unterricht.</div>
    </div>
  );
}

interface LehrerTag { datum: Datum; tag: SchulTag | null; fehler: string | null }

function LehrerWoche(p: { t: Treffer; naechste: boolean; onNaechste: (n: boolean) => void }) {
  const [tage, setTage] = useState<LehrerTag[] | null>(null);
  useEffect(() => {
    const z = zugangAblage.get();
    if (!z) return;
    setTage(null);
    void holeWoche(z, wochenReferenz(p.naechste ? 1 : 0, heuteTag())).then((e) =>
      setTage(e.map(([datum, r]): LehrerTag => (r.art === 'OK' ? { datum, tag: new SchulTag(datum, r.plan), fehler: null } : { datum, tag: null, fehler: r.art === 'KEIN_PLAN' ? 'Kein Plan' : 'Laden fehlgeschlagen' })))
    );
  }, [p.naechste]);
  return (
    <>
      <div class="zeile">
        <Chip an={!p.naechste} onClick={() => p.onNaechste(false)}>Diese Woche</Chip>
        <Chip an={p.naechste} onClick={() => p.onNaechste(true)}>Nächste Woche</Chip>
      </div>
      {tage === null ? <Laden /> : tage.map((wt) => <LehrerWochenKarte key={wt.datum} wt={wt} t={p.t} />)}
    </>
  );
}

function LehrerWochenKarte({ wt, t }: { wt: LehrerTag; t: Treffer }) {
  const tag = wt.tag;
  const stunden = tag?.unterrichtsStunden(t) ?? [];
  const luecken: Freiblock[] = tag?.luecken(t) ?? [];
  const enden = tag?.stundenVon(t).filter((l) => !l.entfaellt).map((l) => l.ende).filter((e): e is number => e !== null) ?? [];
  const schluss = enden.length > 0 ? Math.max(...enden) : null;
  return (
    <Karte>
      <div class="titel-klein" style={{ color: 'var(--on-surface)' }}>{langesDatum(wt.datum)}</div>
      {tag === null ? (
        <div>{wt.fehler ?? 'Kein Plan'}</div>
      ) : stunden.length === 0 ? (
        <div>{tag.faelltGanzAus(t) ? 'Alle Stunden fallen aus' : 'Kein Unterricht'}</div>
      ) : (
        <>
          <div>Unterricht: {stunden.map((n) => `${n}.`).join(' ')} Std{schluss !== null ? ` · Schluss ${uhrzeitText(schluss)}` : ''}</div>
          {luecken.length === 0 ? <div class="sehr-klein">Keine Lücken</div> : luecken.map((b, i) => <div key={i} style={{ color: 'var(--primary)' }}>Frei: {freiblockStundenText(b) ?? ''} ({uhrzeitText(b.beginn)}–{uhrzeitText(b.ende)})</div>)}
        </>
      )}
    </Karte>
  );
}

function Startansicht(p: { tag: SchulTag; istHeute: boolean; jetzt: number; ausCache: boolean; onWahl: (t: Treffer) => void }) {
  const favoriten = favoritenSortiert(useAblage(favoritenAblage));
  const verlaufKeys = useAblage(verlaufAblage);
  const verlauf = verlaufKeys.map((k) => ({ art: k[0] as 'L' | 'R', name: k.slice(2) }) as Treffer);
  const zuletzt = verlauf.filter((t) => !favoriten.some((f) => gleicherTreffer(f, t)));
  const { tag } = p;
  const [stunden, setStunden] = useState<number[]>([]);
  const [gruppe, setGruppe] = useState<string | null>(null);
  const frei = p.istHeute ? tag.freieRaeume(p.jetzt) : null;
  const nummern = [...new Set(tag.raster.map((f) => f.stunde))].sort((a, b) => a - b);
  const freiZuStunden = tag.freieRaeumeIn(new Set(stunden));
  const mitGruppe = frei?.map((x) => [x, raumGruppe(x[0])] as const) ?? [];
  const gruppen = [...new Set(mitGruppe.map((x) => x[1]))].sort();
  const aktiv = gruppe && gruppen.includes(gruppe) ? gruppe : null;
  const sichtbar = mitGruppe.filter((x) => aktiv === null || x[1] === aktiv).map((x) => x[0]);
  return (
    <>
      {p.ausCache && <Karte art="info">Gespeicherter Stand – gerade keine Verbindung.</Karte>}
      {favoriten.length > 0 && (
        <>
          <div class="titel-klein" style={{ color: 'var(--on-surface)' }}>Favoriten</div>
          {favoriten.map((t) => <TrefferZeile key={trefferSchluessel(t)} tag={tag} t={t} istHeute={p.istHeute} jetzt={p.jetzt} onWahl={p.onWahl} />)}
        </>
      )}
      {zuletzt.length > 0 && (
        <div class="stapel" style={{ gap: 6 }}>
          <div class="zeile">
            <span class="titel-klein fuell" style={{ color: 'var(--on-surface)' }}>Zuletzt gesucht</span>
            <button class="textknopf" onClick={verlaufLeeren}>Alle löschen</button>
          </div>
          <div class="scroll-x">
            {zuletzt.map((t) => (
              <span key={trefferSchluessel(t)} class="chip" style={{ paddingRight: 4 }}>
                <span onClick={() => p.onWahl(t)}>{t.name}</span>
                <button class="icon-knopf" style={{ padding: 6 }} aria-label={`${t.name} aus dem Verlauf entfernen`} onClick={() => verlaufEntfernen(t)}>
                  <svg class="ic" style={{ width: 16, height: 16 }} viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" /></svg>
                </button>
              </span>
            ))}
          </div>
        </div>
      )}
      <div class="klein">Finde heraus, wo eine Lehrkraft gerade Unterricht hat oder ob ein Raum frei ist: einfach das Kürzel oder die Raumnummer eingeben.</div>
      {p.istHeute && (
        frei === null ? (
          <div class="klein">Gerade ist kein Unterricht – freie Räume gibt es hier während der Schulzeit.</div>
        ) : (
          <div class="stapel" style={{ gap: 6 }}>
            <div class="titel-klein" style={{ color: 'var(--on-surface)' }}>Jetzt frei ({sichtbar.length})</div>
            {gruppen.length > 1 && (
              <div class="scroll-x">
                <Chip an={aktiv === null} onClick={() => setGruppe(null)}>Alle</Chip>
                {gruppen.map((g) => <Chip key={g} an={aktiv === g} onClick={() => setGruppe(g)}>{g}</Chip>)}
              </div>
            )}
            {frei.length === 0 && <div class="klein">Laut Plan ist gerade jeder Raum belegt.</div>}
            <div class="scroll-x">
              {sichtbar.map(([r, bis]) => <Chip key={r} onClick={() => p.onWahl(raumTreffer(r))}>{bis === null ? r : `${r} · bis ${uhrzeitText(bis)}`}</Chip>)}
            </div>
          </div>
        )
      )}
      {nummern.length > 0 && (
        <div class="stapel" style={{ gap: 6 }}>
          <div class="titel-klein" style={{ color: 'var(--on-surface)' }}>Freie Räume zu einer Stunde</div>
          <div class="sehr-klein">Wähle eine oder mehrere Stunden – gezeigt werden Räume, die in allen gewählten frei sind.</div>
          <div class="scroll-x">
            {nummern.map((n) => <Chip key={n} an={stunden.includes(n)} onClick={() => setStunden(stunden.includes(n) ? stunden.filter((x) => x !== n) : [...stunden, n])}>{n}.</Chip>)}
          </div>
          {stunden.length > 0 && (
            freiZuStunden.length === 0 ? <div class="klein">In diesen Stunden ist laut Plan jeder Raum belegt.</div> : (
              <div class="scroll-x">{freiZuStunden.map((r) => <Chip key={r} onClick={() => p.onWahl(raumTreffer(r))}>{r}</Chip>)}</div>
            )
          )}
        </div>
      )}
      <div class="sehr-klein">Quelle: Schülerplan aller Klassen – ohne Aufsichten und Sprechstunden.</div>
    </>
  );
}

export function Suche() {
  const jetzt = useJetzt();
  const [datum, setDatum] = useState<Datum>(heuteTag());
  const [stand, setStand] = useState<TagStand>({ art: 'LAEDT' });
  const [anfrage, setAnfrage] = useState('');
  const [auswahl, setAuswahl] = useState<Treffer | null>(null);
  const heute = heuteTag();
  const istHeute = datum === heute;

  const laden = (erzwingen = false) => {
    const z = zugangAblage.get();
    if (!z) return;
    setStand({ art: 'LAEDT' });
    void holePlan(z, datum, erzwingen).then((e) => {
      if (e.art === 'OK') setStand({ art: 'OK', tag: new SchulTag(datum, e.plan), ausCache: e.aus === 'CACHE' });
      else if (e.art === 'AUTH') setStand({ art: 'FEHLER', text: 'Benutzername oder Passwort falsch.' });
      else if (e.art === 'KEIN_PLAN') setStand({ art: 'FEHLER', text: 'Für diesen Tag ist kein Plan veröffentlicht.' });
      else setStand({ art: 'FEHLER', text: e.nachricht });
    });
  };
  useEffect(() => laden(), [datum]);

  const waehlen = (t: Treffer) => {
    verlaufMerken(t);
    setAuswahl(t);
  };
  const tagText = istHeute ? 'Heute' : datum === plusTage(heute, 1) ? 'Morgen' : `${wochentagName(datum).slice(0, 2)}., ${kurzesDatum(datum)}`;
  const treffer = stand.art === 'OK' && anfrage.trim() !== '' ? stand.tag.suche(anfrage) : [];
  const favoriten = favoritenSortiert(useAblage(favoritenAblage));
  // Favoriten zuerst, wenn sie auch Treffer sind.
  const geordnet = [...treffer].sort((a, b) => Number(favoriten.some((f) => gleicherTreffer(f, b))) - Number(favoriten.some((f) => gleicherTreffer(f, a))));
  return (
    <div class="seite">
      <div class="tagesleiste">
        <button class="icon-knopf" aria-label="Voriger Tag" onClick={() => setDatum(schultagVersetzt(datum, -1))}><Icon name="links" /></button>
        <div class="mitte">
          <div style={{ fontWeight: 600 }}>{tagText}</div>
          {!istHeute && <button class="textknopf" onClick={() => setDatum(heute)}>Heute</button>}
        </div>
        <button class="icon-knopf" aria-label="Nächster Tag" onClick={() => setDatum(schultagVersetzt(datum, 1))}><Icon name="rechts" /></button>
      </div>
      {auswahl ? (
        <>
          <button class="textknopf" style={{ alignSelf: 'flex-start' }} onClick={() => setAuswahl(null)}>← Zurück zur Suche</button>
          {stand.art === 'OK' ? <Detail tag={stand.tag} t={auswahl} istHeute={istHeute} jetzt={jetzt} /> : <Laden />}
        </>
      ) : (
        <>
          <input type="search" placeholder="Lehrkraft oder Raum" value={anfrage} onInput={(e) => setAnfrage((e.target as HTMLInputElement).value)} autocapitalize="off" />
          {stand.art === 'LAEDT' && <Laden />}
          {stand.art === 'FEHLER' && (
            <>
              <div>{stand.text}</div>
              <button class="knopf" onClick={() => laden(true)}>Erneut versuchen</button>
            </>
          )}
          {stand.art === 'OK' && anfrage.trim() === '' && <Startansicht tag={stand.tag} istHeute={istHeute} jetzt={jetzt} ausCache={stand.ausCache} onWahl={waehlen} />}
          {stand.art === 'OK' && anfrage.trim() !== '' && (
            geordnet.length === 0 ? <div class="klein">Nichts gefunden für „{anfrage.trim()}“.</div> : geordnet.map((t) => <TrefferZeile key={trefferSchluessel(t)} tag={stand.tag} t={t} istHeute={istHeute} jetzt={jetzt} onWahl={waehlen} />)
          )}
        </>
      )}
    </div>
  );
}

void lehrerTreffer;
void FachName;
void useFach;

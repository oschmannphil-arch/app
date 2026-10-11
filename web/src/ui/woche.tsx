import { useEffect, useState } from 'preact/hooks';
import { alsEintraege, blockStundenKurz, blockZusammengefasst } from '../logic/bloecke';
import { hinweisKurz } from '../logic/hinweis';
import type { Lesson } from '../logic/models';
import { wochenReferenz } from '../logic/raster';
import { type Datum, heute as heuteTag, kurzesDatum, uhrzeitText, wochentagName } from '../logic/zeit';
import { designAblage, designAendern } from '../services/daten';
import { useAblage } from '../services/speicher';
import { type WochenZustand, wocheLaden } from '../services/steuerung';
import { Badge, Chip, Karte, Laden, useFach } from './basis';

type Auswahl = -1 | 0 | 1;
const AUSWAHL: Array<[Auswahl, string]> = [[-1, 'Vorherige'], [0, 'Diese Woche'], [1, 'Nächste Woche']];
const ORANGE = '#FF9800';

function WochenStundenZeile(p: { l: Lesson; kurz?: string | null }) {
  const { farbe, anzeige } = useFach();
  const { l } = p;
  const entfall = l.status === 'ENTFALL';
  const vertretung = l.status === 'VERTRETUNG';
  const akzent = entfall ? 'var(--outline)' : l.status === 'NORMAL' ? farbe(l.fach) : ORANGE;
  const hinweis = hinweisKurz(l);
  return (
    <div class="zeile" style={{ opacity: entfall ? 0.4 : 1, padding: '3px 4px', background: vertretung ? '#FF98001A' : 'transparent', borderRadius: 4 }}>
      {vertretung && <div style={{ width: 4, height: 32, borderRadius: 2, background: ORANGE }} />}
      <div style={{ minWidth: 48 }}>
        <div style={{ fontWeight: 600, fontSize: 14 }}>{l.beginn !== null ? uhrzeitText(l.beginn) : `${l.stunde}.`}</div>
        <div class="sehr-klein" style={{ color: 'inherit', opacity: 0.7 }}>{p.kurz ?? ''}</div>
      </div>
      <div class="punkt" style={{ background: akzent, width: 10, height: 10, borderRadius: '50%' }} />
      <div class="fuell">
        <div class={`ellipse ${entfall ? 'durch' : ''}`} style={{ fontSize: 16, fontWeight: 500 }}>{anzeige(l.fach.trim() === '' ? '—' : l.fach)}</div>
        <div class="sehr-klein ellipse" style={{ color: 'inherit', opacity: 0.7 }}>
          {[l.lehrer, hinweis].filter((x) => x.trim() !== '').join(' · ')}
        </div>
      </div>
      <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2 }}>
        {l.istKlausur && !entfall && <Badge farbe="var(--error)" text="var(--on-error)">Klausur</Badge>}
        {l.hatAufgaben && <Badge farbe="#455A64" text="#fff">Aufgaben</Badge>}
        {entfall ? <Badge farbe="var(--error)" text="var(--on-error)">entfällt</Badge> : (
          <>
            {vertretung && <Badge farbe={ORANGE} text="#fff">Vertretung</Badge>}
            {l.raum.trim() !== '' && <span style={{ fontWeight: 600, color: l.raumGeaendert ? '#E65100' : 'inherit' }}>{l.raum}</span>}
          </>
        )}
      </div>
    </div>
  );
}

function TagKarte({ tag, bloecke }: { tag: Extract<WochenZustand, { art: 'GELADEN' }>['tage'][number]; bloecke: boolean }) {
  const heute = tag.datum === heuteTag();
  const stunden = tag.plan?.stunden ?? [];
  const entfaelle = stunden.filter((l) => l.status === 'ENTFALL').length;
  const aenderungen = stunden.filter((l) => l.status !== 'NORMAL' && l.status !== 'ENTFALL').length;
  const klausur = stunden.some((l) => l.istKlausur && !l.entfaellt);
  const eintraege = alsEintraege(stunden, bloecke);
  return (
    <Karte class={heute ? '' : ''} art={undefined} style={heute ? { background: 'var(--primary-container)', color: 'var(--on-primary-container)', borderRadius: 20 } : { borderRadius: 20 }}>
      <div class="zeile zwischen" style={{ alignItems: 'flex-start' }}>
        <div class="fuell">
          <div class="zeile">
            <span style={{ fontSize: 17, fontWeight: 700 }}>{wochentagName(tag.datum)}</span>
            {heute && <Badge farbe="var(--primary)" text="var(--on-primary)">heute</Badge>}
          </div>
          <div class="sehr-klein" style={{ color: 'inherit', opacity: 0.7 }}>{kurzesDatum(tag.datum)}</div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
          {klausur && <Badge farbe="var(--error)" text="var(--on-error)">Klausur</Badge>}
          {entfaelle > 0 && <Badge farbe="var(--error-container)" text="var(--on-error-container)">{entfaelle === 1 ? '1 Entfall' : `${entfaelle} Entfälle`}</Badge>}
          {aenderungen > 0 && <Badge farbe="#FFF3E0" text="#E65100">{aenderungen === 1 ? '1 Änderung' : `${aenderungen} Änderungen`}</Badge>}
        </div>
      </div>
      {tag.fehler ? (
        <div class="sehr-klein" style={{ color: 'inherit', opacity: 0.7, paddingTop: 6 }}>{tag.fehler}</div>
      ) : stunden.length === 0 ? (
        <div class="sehr-klein" style={{ color: 'inherit', opacity: 0.7, paddingTop: 6 }}>Kein Unterricht in deinen Kursen.</div>
      ) : (
        <>
          <hr />
          {eintraege.map((b, i) => (
            <WochenStundenZeile key={`${b.ersteIndex}-${i}`} l={blockZusammengefasst(b)} kurz={bloecke ? blockStundenKurz(b) : null} />
          ))}
        </>
      )}
    </Karte>
  );
}

export function Woche(p: { woche: WochenZustand }) {
  const design = useAblage(designAblage);
  const [auswahl, setAuswahl] = useState<Auswahl>(0);
  const referenz: Datum = wochenReferenz(auswahl, heuteTag());
  useEffect(() => {
    void wocheLaden(referenz);
  }, [referenz]);
  const z = p.woche;
  return (
    <div class="seite">
      <div class="scroll-x">
        {AUSWAHL.map(([a, t]) => (
          <Chip key={a} an={auswahl === a} onClick={() => setAuswahl(a)}>{t}</Chip>
        ))}
      </div>
      <div class="zeile">
        <Chip an={!design.bloecke} onClick={() => designAendern({ bloecke: false })}>Stunden</Chip>
        <Chip an={design.bloecke} onClick={() => designAendern({ bloecke: true })}>Blöcke</Chip>
      </div>
      {z.art !== 'GELADEN' ? (
        <Laden />
      ) : (
        <>
          {z.tage.length > 0 && z.tage.every((t) => t.plan === null && t.fehler === 'Kein Plan') && (
            <div style={{ fontSize: 16, fontWeight: 500 }}>Für diese Woche ist noch kein Plan veröffentlicht – Ferien oder schulfrei?</div>
          )}
          {z.tage.map((t) => (
            <TagKarte key={t.datum} tag={t} bloecke={design.bloecke} />
          ))}
        </>
      )}
    </div>
  );
}

import type { ComponentChildren } from 'preact';
import { useState } from 'preact/hooks';
import { auswerten as ausfallAuswerten, anteilProzent } from '../logic/ausfall';
import { FachName } from '../logic/fachname';
import type { Freund } from '../logic/freizeit';
import { lesen as freundLinkLesen, nachricht as freundNachricht } from '../logic/freundteilen';
import type { Zugang } from '../logic/models';
import { halbjahr, halbjahrGrenzen } from '../logic/noten';
import { heute } from '../logic/zeit';
import { type DesignModus, type FarbThemaName, ausfallAblage, designAblage, designAendern, kursAblage, zugangAblage } from '../services/daten';
import { useAblage } from '../services/speicher';
import { FACH_FARB_AUSWAHL, THEMEN } from '../theme';
import { Chip, Dialog, Karte, Schalter, teilen, useFach } from './basis';
import { FreundeEinstellungen } from './freunde';
import { Patchnotes } from './patchnotes';

const MODI: Array<[DesignModus, string]> = [['SYSTEM', 'System'], ['HELL', 'Hell'], ['DUNKEL', 'Dunkel']];

function FachFarbenDialog(p: { faecher: string[]; onZu: () => void }) {
  const design = useAblage(designAblage);
  const { farbe, anzeige } = useFach();
  const setzen = (fach: string, f: string | null) => {
    const k = FachName.schluessel(fach);
    designAendern({
      fachFarben: f === null ? Object.fromEntries(Object.entries(design.fachFarben).filter(([key]) => key !== k && key !== fach.trim().toLowerCase())) : { ...design.fachFarben, [k]: f }
    });
  };
  return (
    <Dialog titel="Farbe pro Fach" onZu={p.onZu} knoepfe={<button class="textknopf" onClick={p.onZu}>Fertig</button>}>
      {p.faecher.length === 0 && <div>Sobald dein Plan geladen ist, erscheinen hier deine Fächer.</div>}
      {p.faecher.map((fach) => (
        <div key={fach} class="stapel" style={{ gap: 6 }}>
          <div class="zeile">
            <span style={{ width: 14, height: 14, borderRadius: '50%', background: farbe(fach) }} />
            <span class="fuell" style={{ fontWeight: 600 }}>{anzeige(fach)}</span>
            {(FachName.schluessel(fach) in design.fachFarben || fach.trim().toLowerCase() in design.fachFarben) && (
              <button class="textknopf" onClick={() => setzen(fach, null)}>Automatisch</button>
            )}
          </div>
          <div class="scroll-x">
            {FACH_FARB_AUSWAHL.map((f) => <button key={f} class="farbpunkt" aria-label={f} style={{ background: f }} onClick={() => setzen(fach, f)} />)}
          </div>
        </div>
      ))}
    </Dialog>
  );
}

function AusfallKarte() {
  const [offen, setOffen] = useState(false);
  const eintraege = useAblage(ausfallAblage);
  const h = heute();
  const [von, bis] = halbjahrGrenzen(h);
  const e = ausfallAuswerten(eintraege, von, bis, h);
  const { anzeige } = useFach();
  return (
    <Karte>
      <div class="stapel">
        <div style={{ fontSize: 18, fontWeight: 500, color: 'var(--on-surface)' }}>Ausfall-Statistik</div>
        <button class="knopf breit" onClick={() => setOffen(true)}>Ausgefallene Stunden anzeigen</button>
      </div>
      {offen && (
        <Dialog titel={`Ausfälle · ${halbjahr(h)}`} onZu={() => setOffen(false)} knoepfe={<button class="textknopf" onClick={() => setOffen(false)}>Schließen</button>}>
          {e.tage === 0 && e.kommend === 0 ? (
            <div>Noch keine Daten – die App merkt sich den Plan, sobald sie ihn lädt.</div>
          ) : (
            <>
              <div style={{ fontWeight: 600 }}>{e.ausgefallen} von {e.stundenGesamt} Stunden fielen aus ({anteilProzent(e)} %) – an {e.tage} Schultagen.</div>
              {e.kommend > 0 && <div>Dazu kommen {e.kommend} angekündigte Ausfälle.</div>}
              {e.proFach.map(([fach, n]) => <div key={fach} class="tabelle-zeile"><span>{anzeige(fach)}</span><b>{n}</b></div>)}
            </>
          )}
          <div class="sehr-klein">Gezählt wird ab dem ersten Mal, dass die App den Plan geladen hat – Tage davor fehlen. Klausuren zählen nicht als Ausfall.</div>
        </Dialog>
      )}
    </Karte>
  );
}

/** Alle Daten dieser App als Datei sichern bzw. zurückspielen – falls der Browser seinen Speicher einmal leert. */
function SicherungKarte() {
  const [meldung, setMeldung] = useState('');
  const sichern = () => {
    const daten: Record<string, string> = {};
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)!;
      // Das Passwort bleibt bewusst draußen: Die Sicherung ist eine Datei, die man leicht weitergibt.
      if (k.startsWith('nl.') && k !== 'nl.zugang') daten[k] = localStorage.getItem(k)!;
    }
    const blob = new Blob([JSON.stringify({ app: 'nextlesson-web', version: 1, daten }, null, 1)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `nextlesson-sicherung-${heute()}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
    setMeldung('Sicherung gespeichert (ohne Passwort).');
  };
  const einspielen = async (datei: File | undefined) => {
    if (!datei) return;
    try {
      const j = JSON.parse(await datei.text()) as { app?: string; daten?: Record<string, string> };
      if (j.app !== 'nextlesson-web' || !j.daten) throw new Error('falsch');
      for (const [k, v] of Object.entries(j.daten)) if (k.startsWith('nl.') && k !== 'nl.zugang') localStorage.setItem(k, v);
      setMeldung('Sicherung eingespielt – die Seite lädt neu.');
      setTimeout(() => location.reload(), 800);
    } catch {
      setMeldung('Diese Datei ist keine gültige Sicherung.');
    }
  };
  return (
    <Karte>
      <div class="stapel">
        <div style={{ fontSize: 18, fontWeight: 500, color: 'var(--on-surface)' }}>Sicherung</div>
        <div class="klein">Hausaufgaben, Noten, Freunde und Einstellungen liegen nur in diesem Browser. Sichere sie als Datei, falls du das Handy wechselst.</div>
        <button class="knopf breit" onClick={sichern}>Sicherung speichern</button>
        <label class="knopf breit" style={{ textAlign: 'center', cursor: 'pointer' }}>
          Sicherung einspielen
          <input type="file" accept="application/json,.json" style={{ display: 'none' }} onChange={(e) => einspielen((e.target as HTMLInputElement).files?.[0])} />
        </label>
        {meldung && <div class="klein">{meldung}</div>}
      </div>
    </Karte>
  );
}

/** Aufklappbarer Bereich; startet eingeklappt. */
function Abschnitt(p: { titel: string; children: ComponentChildren }) {
  return (
    <details class="abschnitt">
      <summary>{p.titel}</summary>
      <div class="abschnitt-inhalt">{p.children}</div>
    </details>
  );
}

export function Einstellungen(p: {
  faecher: string[];
  freunde: Freund[];
  onZugang: () => void;
  onKurse: () => void;
  onFreundBearbeiten: (f: Freund | null) => void;
  onFreundLink: (text: string) => string;
  onAbmelden: () => void;
}) {
  const design = useAblage(designAblage);
  const zugang = useAblage(zugangAblage) as Zugang | null;
  const kurse = useAblage(kursAblage);
  const [farbDialog, setFarbDialog] = useState(false);
  const [verlauf, setVerlauf] = useState(false);
  const [meldung, setMeldung] = useState('');
  return (
    <div class="seite">
      <Abschnitt titel="Darstellung">
      <Karte>
        <div class="stapel">
          <div class="zeile">{MODI.map(([m, t]) => <Chip key={m} an={design.modus === m} onClick={() => designAendern({ modus: m })}>{t}</Chip>)}</div>
          <div class="sehr-klein">Farbthema</div>
          <div class="scroll-x">
            {Object.entries(THEMEN).map(([k, t]) => <Chip key={k} an={design.thema === k} onClick={() => designAendern({ thema: k as FarbThemaName })}>{t.anzeige}</Chip>)}
          </div>
          <button class="knopf breit" onClick={() => setFarbDialog(true)}>Farbe pro Fach anpassen</button>
          <div class="zeile">
            <div class="fuell">
              <div style={{ color: 'var(--on-surface)', fontSize: 16 }}>Fach-Symbole</div>
              <div class="sehr-klein">Ein Symbol vor dem Fach, z. B. 🔢 Mathe.</div>
            </div>
            <Schalter an={design.symbole} onAendern={(an) => designAendern({ symbole: an })} titel="Fach-Symbole" />
          </div>
        </div>
      </Karte>
      </Abschnitt>

      <Abschnitt titel="Kurse & Freunde">
      <FreundeEinstellungen freunde={p.freunde} onBearbeiten={p.onFreundBearbeiten} onLink={p.onFreundLink} />
      </Abschnitt>

      <Abschnitt titel="Zugang zu Indiware mobil">
      <Karte>
        <div class="stapel">
          {zugang ? (
            <div class="klein">Schule {zugang.schulnummer} · Benutzer {zugang.benutzername} · {kurse.length} {kurse.length === 1 ? 'Kurs' : 'Kurse'} gewählt</div>
          ) : (
            <div class="klein">Nicht angemeldet.</div>
          )}
          <button class="knopf breit" onClick={p.onZugang}>Zugangsdaten ändern</button>
          <button class="knopf breit" onClick={p.onKurse}>Kurse ändern</button>
          {kurse.length > 0 && (
            <button class="knopf breit" onClick={async () => setMeldung(await teilen(freundNachricht(location.href, '', kurse, true), 'Meine Kurse'))}>
              Meine Kurse teilen
            </button>
          )}
          {meldung && <div class="klein">{meldung}</div>}
          <button class="knopf breit" style={{ color: 'var(--error)' }} onClick={p.onAbmelden}>Abmelden und Daten dieses Plans entfernen</button>
        </div>
      </Karte>
      </Abschnitt>

      <Abschnitt titel="Statistik & Sicherung">
        <AusfallKarte />
        <SicherungKarte />
      </Abschnitt>

      <button class="textknopf" onClick={() => setVerlauf(true)}>Web-Version · Was ist neu?</button>
      {farbDialog && <FachFarbenDialog faecher={p.faecher} onZu={() => setFarbDialog(false)} />}
      {verlauf && (
        <Dialog titel="Neuigkeiten" onZu={() => setVerlauf(false)} knoepfe={<button class="textknopf" onClick={() => setVerlauf(false)}>Schließen</button>}>
          <Patchnotes />
        </Dialog>
      )}
    </div>
  );
}

void freundLinkLesen;

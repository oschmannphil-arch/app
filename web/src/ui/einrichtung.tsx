import { useState } from 'preact/hooks';
import type { KursInfo, Zugang } from '../logic/models';
import { deutschesDatum, langesDatum } from '../logic/zeit';
import { AndroidDownload } from './androidapp';
import { Laden } from './basis';
import { KursListe } from './kurse';

/** Anmeldung bei Indiware mobil – der Zugang des Schulplans, nicht ein Konto bei dieser App. */
export function Anmeldung(p: { start: Zugang | null; onSpeichern: (z: Zugang) => void; onAbbrechen?: () => void }) {
  const [schule, setSchule] = useState(p.start?.schulnummer ?? '');
  const [benutzer, setBenutzer] = useState(p.start?.benutzername ?? '');
  const [passwort, setPasswort] = useState(p.start?.passwort ?? '');
  const fertig = schule.trim() !== '' && benutzer.trim() !== '' && passwort.trim() !== '';
  return (
    <div class="seite">
      <div class="karte stapel">
        <div style={{ fontSize: 18, fontWeight: 500, color: 'var(--on-surface)' }}>Zugang zu Indiware mobil</div>
        <div class="klein">
          Das sind die Zugangsdaten deiner Schule für den Vertretungsplan (stundenplan24.de). Sie bleiben auf diesem Gerät gespeichert und gehen nur an den Plan-Server deiner Schule – über den kleinen Weiterleitungsdienst dieser App.
        </div>
        <label class="feld">Schulnummer<input type="text" inputMode="numeric" value={schule} onInput={(e) => setSchule((e.target as HTMLInputElement).value)} autocomplete="off" /></label>
        <label class="feld">Benutzername<input type="text" value={benutzer} onInput={(e) => setBenutzer((e.target as HTMLInputElement).value)} autocapitalize="off" autocomplete="username" /></label>
        <label class="feld">Passwort<input type="password" value={passwort} onInput={(e) => setPasswort((e.target as HTMLInputElement).value)} autocomplete="current-password" /></label>
        <button class="knopf voll breit" disabled={!fertig} onClick={() => p.onSpeichern({ schulnummer: schule.trim(), benutzername: benutzer.trim(), passwort })}>
          Speichern und Plan laden
        </button>
        {p.onAbbrechen && <button class="knopf breit" onClick={p.onAbbrechen}>Abbrechen</button>}
      </div>
      <AndroidDownload mitKarte />
    </div>
  );
}

/** Kurse wählen – beim Einrichten und über "Kurse ändern". */
export function Kurswahl(p: { kurse: KursInfo[]; start: string[]; onSpeichern: (ids: string[]) => void; onAbbrechen?: () => void }) {
  const [auswahl, setAuswahl] = useState<string[]>(p.start);
  return (
    <div class="seite">
      <div class="klein">Kreuze nur deine eigenen Kurse an – die Klasse ergibt sich daraus. Hast du kein Kurssystem, wähle „Ganze Klasse“.</div>
      <KursListe kurse={p.kurse} auswahl={auswahl} onAuswahl={setAuswahl} />
      <button class="knopf voll breit" disabled={auswahl.length === 0} onClick={() => p.onSpeichern(auswahl)}>
        {auswahl.length === 0 ? 'Mindestens einen Kurs wählen' : `Speichern (${auswahl.length})`}
      </button>
      {p.onAbbrechen && <button class="knopf breit" onClick={p.onAbbrechen}>Abbrechen</button>}
    </div>
  );
}

export function FehlerSeite(p: { nachricht: string; zugangsproblem: boolean; onNeu: () => void; onEinstellungen: () => void }) {
  return (
    <div class="vollseite">
      <div style={{ fontSize: 22, fontWeight: 500 }}>Das hat nicht geklappt</div>
      <div class="klein">{p.nachricht}</div>
      <button class="knopf voll" onClick={p.zugangsproblem ? p.onEinstellungen : p.onNeu}>{p.zugangsproblem ? 'Zugang prüfen' : 'Erneut versuchen'}</button>
      {!p.zugangsproblem && <button class="knopf" onClick={p.onEinstellungen}>Einstellungen</button>}
    </div>
  );
}

/** Ferien oder freie Tage. */
export function KeinPlanSeite(p: { naechster: string | null; gesucht: boolean; ferien: string | null; onNeu: () => void }) {
  return (
    <div class="vollseite">
      <div style={{ fontSize: 28, fontWeight: 500 }}>Schulfrei?</div>
      <div class="klein" style={{ fontSize: 16 }}>Für die nächsten Tage ist kein Plan veröffentlicht – vermutlich Ferien oder ein freier Tag.</div>
      {p.ferien && <div style={{ fontWeight: 600 }}>🏖️ {p.ferien} (ohne Gewähr)</div>}
      {p.naechster ? (
        <div style={{ color: 'var(--primary)', fontSize: 17, fontWeight: 500 }}>Der nächste Plan steht für {langesDatum(p.naechster)} bereit.</div>
      ) : p.gesucht ? (
        <div class="klein">In den nächsten vier Wochen ist noch keiner eingestellt. Sobald die Schule einen veröffentlicht, erscheint er hier.</div>
      ) : (
        <div class="klein">Ich schaue nach, wann es weitergeht …</div>
      )}
      <button class="knopf" onClick={p.onNeu}>Erneut prüfen</button>
    </div>
  );
}

export function KeineStundenSeite(p: { onKurse: () => void; onNeu: () => void }) {
  return (
    <div class="vollseite">
      <div style={{ fontSize: 24, fontWeight: 500 }}>Keine Stunden gefunden</div>
      <div class="klein">Es gibt Pläne, aber keine Stunde deiner gewählten Kurse. Meist hat sich die Kursliste geändert – wähle deine Kurse neu.</div>
      <button class="knopf voll" onClick={p.onKurse}>Kurse ändern</button>
      <button class="knopf" onClick={p.onNeu}>Erneut prüfen</button>
    </div>
  );
}

export function LadeSeite() {
  return (
    <div class="vollseite">
      <Laden />
    </div>
  );
}

void deutschesDatum;

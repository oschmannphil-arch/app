import { useEffect, useMemo, useState } from 'preact/hooks';
import { type Pruefung } from '../logic/aufgaben';
import { ferienText } from '../logic/ferien';
import type { Freund } from '../logic/freizeit';
import { lesen as freundLesen } from '../logic/freundteilen';
import { FachName } from '../logic/fachname';
import { type KursInfo, type Lesson, type Zugang, tagesplanFuer } from '../logic/models';
import { hatAenderung, mitNachfolgern, pruefeKurse } from '../logic/kursmigration';
import { systemAusKursIds } from '../logic/noten';
import { type Datum, heute as heuteTag } from '../logic/zeit';
import {
  aufgabenAblage,
  freundLoeschen,
  freundSpeichern,
  freundeAblage,
  hausaufgabeHinzufuegen,
  kursAblage,
  pruefungenAblage,
  zugangAblage
} from '../services/daten';
import { naechsteStundeVon } from '../services/repo';
import { neueId, useAblage } from '../services/speicher';
import { abmelden, aktualisieren, anmelden, kurseSpeichern, kursListeLaden, tagNeuLaden, useZustand } from '../services/steuerung';
import { Dialog, Icon, IconKnopf, type IconName, useFach } from './basis';
import { HausaufgabeDialog, Aufgaben, Klausuren, fotoAnwenden } from './aufgaben';
import { Anmeldung, FehlerSeite, KeinPlanSeite, KeineStundenSeite, Kurswahl, LadeSeite } from './einrichtung';
import { Einstellungen } from './einstellungen';
import { FreundBearbeiten, FreundTag, GemeinsamFrei } from './freunde';
import { Heute, planAlsText } from './heute';
import { Noten } from './noten';
import { Suche } from './suche';
import { Woche } from './woche';
import { teilen } from './basis';
import { planBildTeilen } from './planbild';

type Tab = 'heute' | 'woche' | 'suche' | 'aufgaben' | 'klausuren';
const TABS: Array<[Tab, string, IconName]> = [
  ['heute', 'Heute', 'check'],
  ['woche', 'Woche', 'woche'],
  ['suche', 'Suche', 'suche'],
  ['aufgaben', 'Aufgaben', 'stift'],
  ['klausuren', 'Klausuren', 'liste']
];

type Overlay =
  | { art: 'einstellungen' }
  | { art: 'noten' }
  | { art: 'kurse' }
  | { art: 'zugang' }
  | { art: 'gemeinsam'; start: string[] }
  | { art: 'freund'; id: string }
  | { art: 'freundBearbeiten'; freund: Freund; neu: boolean };

const TITEL: Record<Overlay['art'], string> = {
  einstellungen: 'Einstellungen',
  noten: 'Noten',
  kurse: 'Deine Kurse',
  zugang: 'Zugang',
  gemeinsam: 'Gemeinsam frei',
  freund: 'Freund',
  freundBearbeiten: 'Freund'
};

export function App() {
  const z = useZustand();
  const zugang = useAblage(zugangAblage) as Zugang | null;
  const kurse = useAblage(kursAblage);
  const freunde = useAblage(freundeAblage);
  const aufgaben = useAblage(aufgabenAblage);
  const pruefungen = useAblage(pruefungenAblage);
  const [tab, setTab] = useState<Tab>('heute');
  const [overlay, setOverlayRoh] = useState<Overlay | null>(null);
  const [aufgabeAusStunde, setAufgabeAusStunde] = useState<{ lesson: Lesson; datum: Datum } | null>(null);
  const [naechsteStunde, setNaechsteStunde] = useState<Datum | null>(null);
  const [hinweis, setHinweis] = useState('');
  const [teilenMenue, setTeilenMenue] = useState(false);
  const { anzeige } = useFach();
  void anzeige;

  // Zurück-Taste/Wischgeste des Geräts schließt eine Ansicht statt die App zu verlassen.
  const setOverlay = (o: Overlay | null) => {
    if (o && !overlay) history.pushState({ overlay: true }, '');
    else if (!o && overlay && history.state?.overlay) history.back();
    setOverlayRoh(o);
  };
  useEffect(() => {
    const f = () => setOverlayRoh(null);
    window.addEventListener('popstate', f);
    return () => window.removeEventListener('popstate', f);
  }, []);

  // Beim Zurückkehren in die App (z. B. aus dem Hintergrund) frisch nachsehen.
  useEffect(() => {
    const f = () => {
      if (document.visibilityState === 'visible') {
        void aktualisieren();
        tagNeuLaden();
      }
    };
    document.addEventListener('visibilitychange', f);
    return () => document.removeEventListener('visibilitychange', f);
  }, []);

  // Link von einem Freund ("…/#freund?name=…&kurse=…") öffnet das Formular mit seinen Kursen.
  useEffect(() => {
    const pruefen = () => {
      const f = freundLesen(location.href);
      if (!f) return;
      history.replaceState(null, '', location.pathname + location.search);
      importVorschlagen(f);
    };
    pruefen();
    window.addEventListener('hashchange', pruefen);
    return () => window.removeEventListener('hashchange', pruefen);
  }, [freunde]);

  /** Schlägt [f] zum Speichern vor – außer, genau diesen Freund gibt es schon. Gibt eine Meldung zurück oder ''. */
  function importVorschlagen(f: { name: string; kurse: string[] }): string {
    const vorhanden = freundeAblage.get().find((x) => x.name.trim().toLowerCase() === f.name.trim().toLowerCase() && [...x.kurse].sort().join() === [...f.kurse].sort().join());
    if (vorhanden) {
      const m = `${vorhanden.name || 'Dieser Freund'} ist schon gespeichert.`;
      setHinweis(m);
      return m;
    }
    setOverlayRoh({ art: 'freundBearbeiten', freund: { id: neueId(), name: f.name, kurse: f.kurse }, neu: true });
    history.pushState({ overlay: true }, '');
    return '';
  }

  // Die Stunde im Dialog: nächstes Datum mit Unterricht im selben Kurs nachschlagen.
  useEffect(() => {
    setNaechsteStunde(null);
    if (!aufgabeAusStunde || !zugang) return;
    let aktuell = true;
    void naechsteStundeVon(zugang, new Set(kurse), aufgabeAusStunde.lesson, aufgabeAusStunde.datum).then((d) => aktuell && setNaechsteStunde(d)).catch(() => undefined);
    return () => {
      aktuell = false;
    };
  }, [aufgabeAusStunde]);

  const kursliste: KursInfo[] = z.kursliste;
  const notenSystem = useMemo(() => systemAusKursIds(kurse), [kurse, overlay?.art === 'noten']);
  // Fächer für "Farbe pro Fach" und die Fach-Vorschläge: Plan, gewählte Kurse, Aufgaben, Klausuren.
  const fachNamen = useMemo(() => {
    const ausPlan = z.ui.art === 'ANGEZEIGT' ? z.ui.plan.plan.stunden.map((l) => l.fach) : [];
    const ausWoche = z.woche.art === 'GELADEN' ? z.woche.tage.flatMap((t) => t.plan?.stunden.map((l) => l.fach) ?? []) : [];
    const ausKursen = kurse.map((id) => id.slice(id.indexOf('::') + 2)).filter((k) => k !== '' && k !== '*');
    return FachName.zusammen([...ausPlan, ...ausWoche, ...ausKursen, ...aufgaben.map((h) => h.fach), ...pruefungen.map((p: Pruefung) => p.fach)]);
  }, [z.ui, z.woche, kurse, aufgaben, pruefungen]);
  // Vorschläge fürs Fach-Feld: zusätzlich alle Fächer der ganzen Schule aus dem geladenen Plan.
  const fachVorschlaegeListe = useMemo(() => {
    const schule = z.ui.art === 'ANGEZEIGT' ? z.ui.plan.gesamt.klassen.flatMap((k) => [...k.stunden.map((l) => l.fach), ...k.kurse.map((x) => x.fach)]) : [];
    return FachName.zusammen([...fachNamen, ...schule]);
  }, [fachNamen, z.ui]);

  // Kurse, die es in der aktuellen Kursliste nicht mehr gibt (z. B. nach dem Halbjahreswechsel).
  const migration = useMemo(() => pruefeKurse(kurse, kursliste), [kurse, kursliste]);
  const signatur = useMemo(() => [...Object.entries(migration.ersetzt).map(([a, b]) => `${a}>${b}`), ...migration.fehlend].sort().join('|'), [migration]);
  const [befundBestaetigt, setBefundBestaetigt] = useState(false);
  const [kursHinweisAus, setKursHinweisAus] = useState(false);
  useEffect(() => {
    const merk = (k: string) => { try { return localStorage.getItem('nl.kurs_hinweis.' + k); } catch { return null; } };
    const setze = (k: string, v: string | null) => { try { v === null ? localStorage.removeItem('nl.kurs_hinweis.' + k) : localStorage.setItem('nl.kurs_hinweis.' + k, v); } catch { /* egal */ } };
    if (signatur === '') { setze('verdacht', null); setBefundBestaetigt(false); }
    else if (merk('verdacht') === signatur) setBefundBestaetigt(true);
    else setze('verdacht', signatur);
  }, [signatur]);
  const kursHinweisOffen = hatAenderung(migration) && befundBestaetigt && !kursHinweisAus && !overlay && z.ui.art === 'ANGEZEIGT' && (() => { try { return localStorage.getItem('nl.kurs_hinweis.ignoriert') !== signatur; } catch { return true; } })();

  // ---------- Inhalt ----------
  let inhalt;
  const ui = z.ui;
  if (!zugang || ui.art === 'LOGIN') {
    inhalt = <Anmeldung start={zugang} onSpeichern={anmelden} />;
  } else if (overlay?.art === 'zugang') {
    inhalt = <Anmeldung start={zugang} onSpeichern={(neu) => { setOverlay(null); anmelden(neu); }} onAbbrechen={() => setOverlay(null)} />;
  } else if (ui.art === 'KURSE' && overlay?.art !== 'einstellungen') {
    inhalt = <Kurswahl kurse={ui.kurse} start={kurse} onSpeichern={kurseSpeichern} />;
  } else if (overlay?.art === 'kurse') {
    inhalt = <KurseAendern kursliste={kursliste} start={kurse} onFertig={(ids) => { setOverlay(null); if (ids) kurseSpeichern(ids); }} />;
  } else if (overlay?.art === 'einstellungen') {
    inhalt = (
      <Einstellungen
        faecher={fachNamen}
        freunde={freunde}
        onZugang={() => setOverlayRoh({ art: 'zugang' })}
        onKurse={() => setOverlayRoh({ art: 'kurse' })}
        onFreundBearbeiten={(f) => setOverlayRoh({ art: 'freundBearbeiten', freund: f ?? { id: neueId(), name: '', kurse: [] }, neu: f === null })}
        onFreundLink={(text) => {
          const f = freundLesen(text);
          if (!f) return 'In diesem Text steckt kein Kurs-Link.';
          return importVorschlagen(f);
        }}
        onAbmelden={() => { setOverlay(null); abmelden(); }}
      />
    );
  } else if (overlay?.art === 'noten') {
    inhalt = <Noten system={notenSystem} pruefungen={pruefungen} fachVorschlaege={fachNamen} />;
  } else if (overlay?.art === 'freundBearbeiten') {
    const o = overlay;
    inhalt = (
      <FreundBearbeitenSeite
        freund={o.freund}
        neu={o.neu}
        kursliste={kursliste}
        onSpeichern={(f) => { freundSpeichern(f); setOverlay(null); }}
        onLoeschen={() => { freundLoeschen(o.freund.id); setOverlay(null); }}
        onAbbrechen={() => setOverlay(null)}
      />
    );
  } else if (overlay?.art === 'gemeinsam') {
    inhalt = <GemeinsamFrei freunde={freunde} startAuswahl={overlay.start} />;
  } else if (overlay?.art === 'freund') {
    const f = freunde.find((x) => x.id === overlay.id);
    inhalt =
      f && ui.art === 'ANGEZEIGT' ? (
        <FreundTag freund={f} persoenlich={ui.plan} onWoche={() => setOverlayRoh({ art: 'gemeinsam', start: [f.id] })} onBearbeiten={() => setOverlayRoh({ art: 'freundBearbeiten', freund: f, neu: false })} />
      ) : (
        <LadeSeite />
      );
  } else if (tab === 'heute') {
    switch (ui.art) {
      case 'LAEDT':
        inhalt = <LadeSeite />;
        break;
      case 'ANGEZEIGT':
        inhalt = (
          <Heute
            pp={ui.plan}
            tag={z.tag}
            aktualisiert={z.aktualisiert}
            onAufgaben={() => setTab('aufgaben')}
            onKlausuren={() => setTab('klausuren')}
            onStunde={(lesson, datum) => setAufgabeAusStunde({ lesson, datum })}
            onFreund={(id) => setOverlay({ art: 'freund', id })}
            onGemeinsam={() => setOverlay({ art: 'gemeinsam', start: freunde.map((f) => f.id) })}
          />
        );
        break;
      case 'KEINE_STUNDEN':
        inhalt = <KeineStundenSeite onKurse={() => setOverlay({ art: 'kurse' })} onNeu={() => void aktualisieren(true)} />;
        break;
      case 'KEIN_PLAN':
        inhalt = <KeinPlanSeite naechster={ui.naechster} gesucht={ui.gesucht} ferien={ferienText(heuteTag())} onNeu={() => void aktualisieren(true)} />;
        break;
      case 'FEHLER':
        inhalt = <FehlerSeite nachricht={ui.nachricht} zugangsproblem={ui.zugangsproblem} onNeu={() => void aktualisieren(true)} onEinstellungen={() => setOverlay({ art: 'einstellungen' })} />;
        break;
      default:
        inhalt = <LadeSeite />;
    }
  } else if (tab === 'woche') {
    inhalt = <Woche woche={z.woche} />;
  } else if (tab === 'suche') {
    inhalt = <Suche />;
  } else if (tab === 'aufgaben') {
    inhalt = <Aufgaben faecher={fachVorschlaegeListe} />;
  } else {
    inhalt = <Klausuren faecher={fachVorschlaegeListe} />;
  }

  const einrichtung = !zugang || ui.art === 'LOGIN' || (ui.art === 'KURSE' && overlay?.art !== 'einstellungen') || overlay?.art === 'zugang' || overlay?.art === 'kurse';
  const titel = overlay ? (overlay.art === 'freund' ? (freunde.find((f) => f.id === overlay.id)?.name ?? 'Freund') : overlay.art === 'freundBearbeiten' ? (overlay.neu ? 'Freund hinzufügen' : overlay.freund.name) : TITEL[overlay.art]) : !zugang ? 'Einstellungen' : ui.art === 'KURSE' ? 'Deine Kurse' : TABS.find((t) => t[0] === tab)![1];
  const offeneAufgaben = aufgaben.filter((h) => !h.erledigt).length;

  return (
    <>
      <header class="kopf">
        {overlay && overlay.art !== 'zugang' && <IconKnopf icon="zurueck" titel="Zurück" onClick={() => setOverlay(null)} />}
        <h1>{titel}</h1>
        {!einrichtung && !overlay && (tab === 'heute' || tab === 'woche') && (
          <IconKnopf icon="neu" titel="Aktualisieren" onClick={() => { void aktualisieren(true); tagNeuLaden(); }} />
        )}
        {!einrichtung && !overlay && tab === 'heute' && ui.art === 'ANGEZEIGT' && (
          <IconKnopf icon="teilen" titel="Plan teilen" onClick={() => setTeilenMenue(true)} />
        )}
        {!einrichtung && !overlay && (tab === 'heute' || tab === 'klausuren') && <IconKnopf icon="stern" titel="Noten" onClick={() => setOverlay({ art: 'noten' })} />}
        {zugang && ui.art !== 'LOGIN' && ui.art !== 'KURSE' && (
          <IconKnopf icon="einstellungen" titel="Einstellungen" onClick={() => (overlay?.art === 'einstellungen' ? setOverlay(null) : overlay ? setOverlayRoh({ art: 'einstellungen' }) : setOverlay({ art: 'einstellungen' }))} />
        )}
      </header>
      <main class="inhalt">{inhalt}</main>
      {teilenMenue && ui.art === 'ANGEZEIGT' && (
        <Dialog
          titel="Plan teilen"
          onZu={() => setTeilenMenue(false)}
          knoepfe={<button class="textknopf" onClick={() => setTeilenMenue(false)}>Abbrechen</button>}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button class="knopf breit" onClick={async () => { setTeilenMenue(false); setHinweis(await teilen(planAlsText(ui.plan), 'Plan teilen')); }}>Als Text</button>
            <button class="knopf breit" onClick={async () => { setTeilenMenue(false); setHinweis(await planBildTeilen(ui.plan)); }}>Als Bild</button>
          </div>
        </Dialog>
      )}
      {!einrichtung && !overlay && (
        <nav class="navi">
          {TABS.map(([t, name, icon]) => (
            <button key={t} class={tab === t ? 'aktiv' : ''} onClick={() => setTab(t)}>
              <span class="blase"><Icon name={icon} /></span>
              {t === 'aufgaben' && offeneAufgaben > 0 && <span class="zaehler">{offeneAufgaben}</span>}
              {name}
            </button>
          ))}
        </nav>
      )}

      {aufgabeAusStunde && (
        <HausaufgabeDialog
          vorschlagFach={aufgabeAusStunde.lesson.fach}
          naechsteStunde={naechsteStunde}
          faecher={fachVorschlaegeListe}
          onZu={() => setAufgabeAusStunde(null)}
          onSpeichern={(fach, text, faellig, foto) => {
            void fotoAnwenden(hausaufgabeHinzufuegen(fach, text, faellig), foto);
            setAufgabeAusStunde(null);
          }}
        />
      )}
      {hinweis && (
        <Dialog onZu={() => setHinweis('')} knoepfe={<button class="textknopf" onClick={() => setHinweis('')}>OK</button>}>
          <div>{hinweis}</div>
        </Dialog>
      )}
      {kursHinweisOffen && (
        <Dialog
          titel="Deine Kurse haben sich geändert"
          onZu={() => { try { localStorage.setItem('nl.kurs_hinweis.ignoriert', signatur); } catch { /* egal */ } setKursHinweisAus(true); }}
          knoepfe={
            <>
              <button class="textknopf" onClick={() => { try { localStorage.setItem('nl.kurs_hinweis.ignoriert', signatur); } catch { /* egal */ } setKursHinweisAus(true); }}>Später</button>
              <button
                class="textknopf"
                onClick={() => {
                  // Nur Nachfolger einsetzen – die fehlenden Kurse nimmt man in der Kurswahl selbst heraus.
                  if (Object.keys(migration.ersetzt).length > 0) kurseSpeichern(mitNachfolgern(kurse, migration));
                  setKursHinweisAus(true);
                  if (migration.fehlend.length > 0) setOverlay({ art: 'kurse' });
                }}
              >
                {migration.fehlend.length > 0 ? 'Kurse wählen' : 'Übernehmen'}
              </button>
            </>
          }
        >
          {Object.keys(migration.ersetzt).length > 0 && (
            <>
              <div>Diese Kurse gibt es nicht mehr – ein Nachfolger passt:</div>
              {Object.entries(migration.ersetzt).map(([a, b]) => <div key={a}>• {a.split('::')[1]} → {b.split('::')[1]}</div>)}
            </>
          )}
          {migration.fehlend.length > 0 && (
            <>
              <div>Diese Kurse stehen nicht mehr in der Kursliste:</div>
              {migration.fehlend.map((k) => <div key={k}>• {k.split('::')[1]} ({k.split('::')[0]})</div>)}
              <div>Wähle sie neu aus, damit dein Plan wieder stimmt.</div>
            </>
          )}
        </Dialog>
      )}
    </>
  );
}

/** "Kurse ändern": lädt die Kursliste, falls sie noch fehlt. */
function KurseAendern(p: { kursliste: KursInfo[]; start: string[]; onFertig: (ids: string[] | null) => void }) {
  const [liste, setListe] = useState<KursInfo[]>(p.kursliste);
  const [laedt, setLaedt] = useState(p.kursliste.length === 0);
  useEffect(() => {
    if (p.kursliste.length > 0) return;
    void kursListeLaden().then((k) => { setListe(k); setLaedt(false); });
  }, []);
  if (laedt) return <LadeSeite />;
  if (liste.length === 0) return <FehlerSeite nachricht="Die Kursliste konnte nicht geladen werden." zugangsproblem={false} onNeu={() => { setLaedt(true); void kursListeLaden().then((k) => { setListe(k); setLaedt(false); }); }} onEinstellungen={() => p.onFertig(null)} />;
  return <Kurswahl kurse={liste} start={p.start} onSpeichern={(ids) => p.onFertig(ids)} onAbbrechen={() => p.onFertig(null)} />;
}

function FreundBearbeitenSeite(p: { freund: Freund; neu: boolean; kursliste: KursInfo[]; onSpeichern: (f: Freund) => void; onLoeschen: () => void; onAbbrechen: () => void }) {
  const [liste, setListe] = useState<KursInfo[]>(p.kursliste);
  const [laedt, setLaedt] = useState(p.kursliste.length === 0);
  useEffect(() => {
    if (p.kursliste.length > 0) return;
    void kursListeLaden().then((k) => { setListe(k); setLaedt(false); });
  }, []);
  if (laedt) return <LadeSeite />;
  return <FreundBearbeiten freund={p.freund} istNeu={p.neu} kurse={liste} onSpeichern={p.onSpeichern} onLoeschen={p.onLoeschen} onAbbrechen={p.onAbbrechen} />;
}

void tagesplanFuer;

import { useMemo, useState } from 'preact/hooks';
import { type KursInfo, istGanzeKlasse, kursAnzeige, kursId, kursSuchtext } from '../logic/models';
import { gruppieren } from '../logic/text';

/** Kurse ankreuzen: nach Klasse gruppiert, mit Suchfeld. [auswahl] sind Kurs-IDs ("Klasse::Kürzel"). */
export function KursListe(p: { kurse: KursInfo[]; auswahl: string[]; onAuswahl: (neu: string[]) => void }) {
  const [suche, setSuche] = useState('');
  const gruppen = useMemo(() => {
    const q = suche.trim().toLowerCase();
    const sichtbar = q === '' ? p.kurse : p.kurse.filter((k) => kursSuchtext(k).includes(q));
    return [...gruppieren(sichtbar, (k) => k.klasse).entries()];
  }, [p.kurse, suche]);
  const gewaehlt = new Set(p.auswahl);
  const umschalten = (k: KursInfo) => {
    const id = kursId(k);
    p.onAuswahl(gewaehlt.has(id) ? p.auswahl.filter((x) => x !== id) : [...p.auswahl, id]);
  };
  return (
    <div class="stapel">
      <input type="search" placeholder="Suchen (Kürzel, Fach, Lehrer, Klasse)" value={suche} onInput={(e) => setSuche((e.target as HTMLInputElement).value)} />
      {p.kurse.length === 0 && <div class="klein">Keine Kurse in der Kursliste gefunden.</div>}
      {gruppen.map(([klasse, kurse]) => (
        <div key={klasse} class="karte">
          <div class="titel-klein" style={{ color: 'var(--on-surface)', fontSize: 16 }}>{klasse}</div>
          {kurse.map((k) => (
            <label key={kursId(k)} class="zeile" style={{ padding: '8px 0', cursor: 'pointer' }}>
              <input type="checkbox" checked={gewaehlt.has(kursId(k))} onChange={() => umschalten(k)} style={{ width: 20, height: 20, accentColor: 'var(--primary)' }} />
              <span style={{ color: 'var(--on-surface)', fontWeight: istGanzeKlasse(k) ? 600 : 400 }}>{kursAnzeige(k)}</span>
            </label>
          ))}
        </div>
      ))}
    </div>
  );
}

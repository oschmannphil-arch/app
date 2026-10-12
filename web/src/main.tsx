import { render } from 'preact';
import { useEffect } from 'preact/hooks';
import './styles.css';
import { DICHTE_PX, beimStartAufraeumen, designAblage, zugangAblage } from './services/daten';
import { konfigurationLaden } from './services/api';
import { useAblage } from './services/speicher';
import { speicherAnfragen } from './services/sicherung';
import { aktualisieren } from './services/steuerung';
import { paletteAnwenden } from './theme';
import { useDunkel } from './ui/basis';
import { App } from './ui/app';

function Huelle() {
  const design = useAblage(designAblage);
  const dunkel = useDunkel();
  // Die Farben setzen, bevor etwas gezeichnet wird, damit nichts aufblitzt.
  const ton = design.eigenerTon ?? 30;
  paletteAnwenden(dunkel, design.thema, ton);
  useEffect(() => paletteAnwenden(dunkel, design.thema, ton), [dunkel, design.thema, ton]);
  useEffect(() => {
    document.documentElement.style.setProperty('--zeile-abstand', `${DICHTE_PX[design.dichte ?? 'NORMAL']}px`);
  }, [design.dichte]);
  return <App />;
}

beimStartAufraeumen();
void speicherAnfragen();
void konfigurationLaden().then(() => {
  if (zugangAblage.get()) void aktualisieren();
  else void aktualisieren();
});
render(<Huelle />, document.getElementById('app')!);

// Offline-Fähigkeit und Installation auf dem Home-Bildschirm.
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('sw.js').catch(() => undefined);
    let neuGeladen = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (neuGeladen) return;
      neuGeladen = true;
      location.reload();
    });
  });
}

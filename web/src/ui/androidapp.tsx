import { useEffect, useState } from 'preact/hooks';
import { Karte } from './basis';

const REPO = 'oschmannphil-arch/app';
const APK_NAME = 'NaechsteStunde.apk';
/** Fallback, falls die Suche nach dem neuesten Build scheitert: die Release-Seite mit allen Builds. */
const RELEASES = `https://github.com/${REPO}/releases`;

interface Angebot {
  build: number;
  url: string;
}

/** Der neueste Build mit APK: wie die Android-App sucht auch die Web-Seite in den Releases (Builds sind Vorab-Releases). */
async function neuesterBuild(): Promise<Angebot | null> {
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=15`, { headers: { Accept: 'application/vnd.github+json' } });
    if (!r.ok) return null;
    const liste = (await r.json()) as Array<{ tag_name?: string; assets?: Array<{ name: string; browser_download_url: string }> }>;
    let beste: Angebot | null = null;
    for (const rel of liste) {
      const build = Number(/^build-(\d+)$/.exec(rel.tag_name ?? '')?.[1]);
      const apk = rel.assets?.find((a) => a.name === APK_NAME);
      if (!Number.isFinite(build) || !apk) continue;
      if (!beste || build > beste.build) beste = { build, url: apk.browser_download_url };
    }
    return beste;
  } catch {
    return null;
  }
}

/** Download der Android-App (APK) mit dem Hinweis, dass sie nur für Android ist. */
export function AndroidDownload(p: { mitKarte?: boolean }) {
  const [angebot, setAngebot] = useState<Angebot | null | undefined>(undefined);
  useEffect(() => {
    let aktiv = true;
    void neuesterBuild().then((a) => aktiv && setAngebot(a));
    return () => {
      aktiv = false;
    };
  }, []);
  const inhalt = (
    <div class="stapel">
      <div style={{ fontSize: 18, fontWeight: 500, color: 'var(--on-surface)' }}>Android-App</div>
      <div class="klein">Die App mit Widget und Benachrichtigungen bei Ausfall – als Datei zum Installieren (APK).</div>
      <a class="knopf voll breit" style={{ textAlign: 'center', textDecoration: 'none' }} href={angebot?.url ?? RELEASES} {...(angebot ? { download: APK_NAME } : { target: '_blank', rel: 'noopener' })}>
        {angebot ? `Android-App herunterladen (Build ${angebot.build})` : angebot === undefined ? 'Android-App herunterladen' : 'Android-App herunterladen (Release-Seite)'}
      </a>
      <div class="sehr-klein" style={{ fontWeight: 600 }}>Nur für Android.</div>
    </div>
  );
  return p.mitKarte ? <Karte>{inhalt}</Karte> : inhalt;
}

# NextLesson Web (PWA)

Browser-Version für iPhones (und alles andere). Kostenlos: GitHub Pages + Cloudflare Worker.

## Aufbau
Der Browser darf stundenplan24.de nicht direkt abfragen (CORS). Deshalb läuft ein kleiner
Proxy (`worker/proxy.js`) als Cloudflare Worker. Er leitet nur Plan-Dateien weiter
(`stundenplan24.de/<schule>/mobil/mobdaten/PlanKl<datum>.xml`). Deine Zugangsdaten laufen
dabei durch deinen eigenen Worker, werden aber nicht gespeichert.

## Einrichtung
1. **Worker**: Cloudflare-Konto (kostenlos) → Workers & Pages → Worker erstellen → Inhalt von
   `worker/proxy.js` einfügen → deployen. Unter Settings → Variables
   `ERLAUBTE_URSPRUENGE` = `https://<dein-name>.github.io` setzen.
2. **Proxy-Adresse**: In `public/config.json` `{"proxyUrl": "https://<worker>.workers.dev"}` eintragen, committen.
3. **GitHub Pages**: Repo → Settings → Pages → Source: Branch `gh-pages`, Ordner `/ (root)`.
   Der Workflow `.github/workflows/web.yml` baut und veröffentlicht bei jedem Push auf `web/**`.
4. **iPhone**: Seite in Safari öffnen → Teilen → „Zum Home-Bildschirm“.

## Entwicklung
```
npm ci
npm run dev        # lokaler Server
npm test           # Logik-Tests
npm run build
```

## Nicht möglich im Web
Widget, Hintergrund-Benachrichtigungen, Auto-Updater, App-Icon-Shortcuts.

/**
 * Weiterleitungsdienst für die NextLesson-Web-App (Cloudflare Worker).
 *
 * Browser dürfen stundenplan24.de nicht direkt abfragen (CORS). Dieser Dienst nimmt die Anfrage der
 * Web-App entgegen, reicht sie mit den Zugangsdaten (Basic-Auth-Kopf) an den Plan-Server weiter und
 * gibt die Antwort mit den nötigen CORS-Köpfen zurück.
 *
 * - Er speichert nichts und schreibt keine Zugangsdaten ins Protokoll.
 * - Er spricht ausschließlich mit https://www.stundenplan24.de/<Schulnummer>/mobil/mobdaten/PlanKl<Datum>.xml.
 * - Mit der Variable ERLAUBTE_URSPRUENGE (kommagetrennte Adressen, z. B. https://nutzer.github.io)
 *   lässt er nur deine Web-App zu. Ohne die Variable ist jede Seite erlaubt.
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const origin = request.headers.get('Origin') || '';
    const erlaubt = (env.ERLAUBTE_URSPRUENGE || '*')
      .split(',')
      .map((s) => s.trim().replace(/\/+$/, ''))
      .filter(Boolean);
    const ok = erlaubt.includes('*') || (origin !== '' && erlaubt.includes(origin));
    const cors = {
      'Access-Control-Allow-Origin': ok ? (erlaubt.includes('*') ? '*' : origin) : 'null',
      'Access-Control-Allow-Headers': 'Authorization',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Max-Age': '86400',
      Vary: 'Origin'
    };

    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
    if (!ok) return new Response('Diese Adresse darf den Dienst nicht nutzen.', { status: 403, headers: cors });
    if (request.method !== 'GET' || url.pathname !== '/plan') return new Response('Nicht gefunden', { status: 404, headers: cors });

    const schule = url.searchParams.get('schule') || '';
    const datum = url.searchParams.get('datum') || '';
    if (!/^\d{1,10}$/.test(schule) || !/^\d{8}$/.test(datum)) return new Response('Ungültige Anfrage', { status: 400, headers: cors });
    const auth = request.headers.get('Authorization');
    if (!auth || !auth.startsWith('Basic ')) return new Response('Anmeldung fehlt', { status: 401, headers: cors });

    let antwort;
    try {
      antwort = await fetch(`https://www.stundenplan24.de/${schule}/mobil/mobdaten/PlanKl${datum}.xml`, {
        headers: { Authorization: auth, 'User-Agent': 'Indiware', 'Cache-Control': 'no-cache' }
      });
    } catch {
      return new Response('Plan-Server nicht erreichbar', { status: 502, headers: cors });
    }
    // Nur Status und Inhalt weitergeben – "WWW-Authenticate" würde im Browser ein Passwortfenster öffnen.
    return new Response(await antwort.arrayBuffer(), {
      status: antwort.status,
      headers: {
        ...cors,
        'Content-Type': antwort.headers.get('Content-Type') || 'text/xml; charset=utf-8',
        'Cache-Control': 'no-store'
      }
    });
  }
};

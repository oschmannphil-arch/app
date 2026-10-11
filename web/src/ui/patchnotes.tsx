/** Was die Web-Version kann – und was bewusst nicht (verglichen mit der Android-App). */
export function Patchnotes() {
  return (
    <div class="stapel">
      <div class="titel-klein" style={{ color: 'var(--on-surface)' }}>Das kann die Web-Version</div>
      <div>• Plan, Woche, Blöcke, Wischen zwischen den Tagen, Klausuren, Ausfall und Vertretungen wie in der App.</div>
      <div>• Suche nach Lehrkräften und Räumen, freie Räume, Freunde und gemeinsame Freistunden.</div>
      <div>• Hausaufgaben, Klausuren, Noten (Punkte oder 1–6), Ziele, Prognose, Zeugnis-Vorschau, Abi-Rechner.</div>
      <div>• Farbthemen, Fach-Symbole, Ausfall-Statistik, Ferien-Countdown.</div>
      <div>• „Seit gestern geändert“ in der Heute-Ansicht, Plan als Bild teilen, Tafel-Foto an Hausaufgaben.</div>
      <div class="titel-klein" style={{ color: 'var(--on-surface)', paddingTop: 8 }}>Das geht im Browser nicht</div>
      <div>• Widget und Benachrichtigungen bei Ausfall – dafür gibt es die Android-App.</div>
      <div>• Hintergrund-Aktualisierung: Die Web-App lädt den Plan beim Öffnen.</div>
    </div>
  );
}

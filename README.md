# Nächste Stunde – Android-App für Stundenplan24 / Indiware mobil

Zeigt dir deine nächste Unterrichtsstunde (Fach, Raum, Zeit) an und bietet ein
Homescreen-Widget, das automatisch im Hintergrund aktualisiert wird.

## Setup in Android Studio

1. Android Studio installieren (falls noch nicht vorhanden): https://developer.android.com/studio
2. Diesen entpackten Ordner über **File → Open…** in Android Studio öffnen.
3. Android Studio fragt beim ersten Öffnen nach dem Gradle-Wrapper – einfach
   bestätigen ("Trust project" / "Sync Now"). Der Wrapper (`gradlew`) wird
   automatisch ergänzt; es ist keine manuelle Installation von Gradle nötig.
4. Ein Gerät oder einen Emulator verbinden (Android 8.0 / API 26 oder neuer).
5. Auf **Run ▶** klicken. Die App installiert sich und startet.
6. Beim ersten Start gibst du **Schulnummer, Benutzername und Passwort** ein.
   Danach kreuzt du **nur noch deine Kurse an** – mehr nicht. Die Klasse bzw.
   den Jahrgang leitet die App selbst daraus ab. Die Liste ist durchsuchbar
   (Kurs, Fach, Lehrer, Jahrgang); Kurse werden als `D1 · De (Müller)`
   angezeigt. Für Klassen ohne Kurssystem steht in derselben Liste pro Klasse
   ein Eintrag **"Ganze Klasse – alle Stunden"**.

   Die Zugangsdaten werden **ausschließlich verschlüsselt auf deinem Gerät**
   gespeichert (AES-256 über `EncryptedSharedPreferences`) und nur direkt an
   `stundenplan24.de` per HTTPS gesendet.
7. Widget hinzufügen: lange auf den Homescreen drücken → Widgets → "Nächste
   Stunde" auswählen und platzieren. Ein Tipp auf das Widget löst eine
   sofortige Aktualisierung aus.

## Widget-Größen

Das Widget wird standardmäßig als **2×2** eingesetzt und lässt sich frei größer
ziehen. Es hat zwei Layouts, weil sich derselbe Text nicht in jeder Breite
sinnvoll unterbringen lässt:

* **2×2 (schmal)** – kurzer Kopf ("morgen", "3. Stunde"), Fach groß, darunter Raum
  und Uhrzeit **untereinander**. Nebeneinander würde "Raum 226 · 07:15–08:00" sonst
  mitten im Text umbrechen.
* **ab 3 Zellen Breite** – ausgeschriebener Kopf, Raum und Zeit nebeneinander,
  zusätzlich Lehrer und die Zeile "geprüft HH:MM".

Umgesetzt ist das über Glance mit `SizeMode.Responsive`; Android wählt anhand der
tatsächlichen Größe automatisch die passende Fassung.

Die Farben folgen dem Systemdesign: heller Hintergrund mit dunklem Text, im
Dunkelmodus umgekehrt. Fällt die Stunde aus oder gibt es eine Vertretung, wechselt
der Hintergrund auf einen Rotton.

## Das Farbschema ("Mocha")

Warmes Dunkelbraun als Grund, Karamell für die Karte der nächsten Stunde und den
heutigen Tag, gedecktes Schieferblau für die Stundenliste. Die Werte stehen fest in
`ui/theme/Color.kt`.

**Material You ist bewusst abgeschaltet.** Wäre es an, würde Android die Farben aus
dem Hintergrundbild ableiten – die App sähe dann auf jedem Gerät und nach jedem
Wallpaper-Wechsel anders aus. Wer das lieber möchte, ruft das Theme mit
`dynamischeFarben = true` auf.

Hell- und Dunkelfassung folgen der Systemeinstellung. Soll die App **immer** dunkel
bleiben, in `ui/theme/Theme.kt` den Vorgabewert auf `dunkel: Boolean = true` setzen.

Nur zwei Dateien tragen das ganze Design: `ui/theme/Color.kt` und `ui/theme/Theme.kt`.

## Die vier Tabs

Unten in der App gibt es vier Bereiche:

* **Heute** – die nächste Stunde groß, darunter der Tagesverlauf.
* **Woche** – Montag bis Freitag am Stück.
* **Aufgaben** – Hausaufgaben eintragen und abhaken.
* **Klausuren** – Tests und Klausuren mit Datum.

## Hausaufgaben

Über den Knopf unten rechts anlegen: Fach, was zu tun ist, und wahlweise ein
Fälligkeitsdatum (Schnellwahl "Heute"/"Morgen" oder Kalender). Antippen hakt eine
Aufgabe ab.

**Abgehakte Aufgaben bleiben bis zum nächsten App-Start sichtbar** und verschwinden
dann automatisch – so sieht man noch, was man gerade geschafft hat, ohne dass sich
die Liste langfristig zumüllt. Wer sofort löschen will, nutzt das Papierkorb-Symbol.
Überfällige Aufgaben werden rot markiert.

## Tests und Klausuren

Ebenfalls über den Knopf unten rechts: Art (Klausur oder Test), Fach, optional Thema
und Notiz, dazu das Datum. Die Karten zeigen den Countdown ("in 5 T.", "morgen",
"heute") und färben sich ab drei Tagen vor dem Termin rot.

**Zum Datum:** Datumsangaben werden als Zahl gespeichert (Tage seit 1970), nicht als
formatierter Text. Genau daher kommt der bekannte Fehler, dass Termine nach einem
Neustart plötzlich auf dem 01.01.0001 stehen – ein leerer Textwert wird beim Einlesen
zu einem Nulldatum. Fehlt in der Ablage wider Erwarten doch ein Datum, wird der
Eintrag übersprungen statt mit einem falschen Datum angezeigt.

Vergangene Termine rutschen in den Abschnitt "Vorbei" und werden eine Woche nach dem
Termin automatisch entfernt.

## Tägliche Lern-Erinnerung

Unter *Einstellungen* (Zahnrad oben rechts) lässt sich eine Uhrzeit einstellen, zu der
die App täglich an Hausaufgaben und anstehende Klausuren erinnert. Die Meldung nennt
die Zahl der offenen Aufgaben und die nächste Klausur.

Technisch plant sich diese Erinnerung nach jedem Auslösen selbst für den nächsten Tag
neu ein. Ein einfacher 24-Stunden-Job würde über die Wochen von der Wunschzeit
wegdriften; so wird die eingestellte Uhrzeit jeden Tag neu getroffen.

## Benachrichtigungen bei Entfall

Es gibt genau dann eine Push-Meldung, wenn eine Stunde **in deinen gewählten Kursen
neu ausfällt**. Nicht bei Raumwechsel, nicht bei Vertretung, und nicht noch einmal
für einen Entfall, den du schon kennst.

Dahinter steckt ein Abgleich (`EntfallTracker`): Die App merkt sich pro Tag, welche
deiner Stunden zuletzt als ausgefallen gemeldet waren, und meldet nur die Differenz.

Drei Regeln, damit es nicht nervt:

* **Erster Abruf für einen Tag meldet nie.** Sonst käme direkt nach dem Einrichten
  eine Flut von Meldungen für längst bekannte Entfälle.
* **Vergangene Stunden werden nicht gemeldet.** Um 18 Uhr noch zu erfahren, dass die
  3. Stunde ausgefallen ist, hilft niemandem.
* **Nach einer Kursänderung wird der Stand zurückgesetzt**, weil er sich auf andere
  Kurse bezog.

Android 13 und neuer fragt beim ersten Start nach der Erlaubnis für Benachrichtigungen.

## Wie oft aktualisiert wird

Zwei Zeitangaben werden unten in der App getrennt angezeigt, weil sie oft verwechselt
werden:

* **Plan-Stand der Schule** – wann *die Schule* den Plan zuletzt erzeugt hat
  (`<zeitstempel>` aus der XML). Steht dort 12:50, hat die Schule seit 12:50 nichts
  Neues veröffentlicht. Daran kann die App nichts ändern.
* **Zuletzt geprüft** – wann *die App* zuletzt nachgesehen hat. Bleibt dieser Wert
  stehen, läuft die Hintergrundaktualisierung nicht.

Geprüft wird alle **15 Minuten** (das kürzeste Intervall, das Androids WorkManager für
periodische Arbeit zulässt), zusätzlich bei jedem Öffnen der App und bei jedem Tippen
auf das Widget. Abgerufen werden dabei heute und die zwei folgenden Tage – so kommt der
Entfall von morgen schon am Abend vorher an. Die Anfragen laufen mit `no-cache`, damit
keine veraltete Antwort aus einem Zwischenspeicher kommt.

Falls die Aktualisierung auf deinem Xiaomi einschläft: MIUI/HyperOS beendet
Hintergrundprozesse aggressiv. In den Einstellungen unter *Apps → Nächste Stunde →
Akku* auf **"Keine Einschränkungen"** stellen und die App im Task-Manager über das
Schloss-Symbol fixieren.

## Wie es funktioniert

- Die App lädt `https://www.stundenplan24.de/{Schulnummer}/mobil/mobdaten/PlanKl{JJJJMMTT}.xml`
  per HTTP Basic Auth (Benutzername/Passwort deiner Schule).
- Ist für den heutigen Tag kein Plan da (Wochenende, Ferien, noch nicht
  veröffentlicht), probiert die App automatisch die nächsten Tage durch.
- Angezeigt wird die laufende bzw. als nächstes anstehende Stunde. Endet die
  laufende Stunde in 5 Minuten oder weniger, springt die Anzeige schon auf die
  folgende ("Gleich vorbei — als Nächstes").
- **Ist der Schultag vorbei, zeigen App und Widget die erste Stunde des nächsten
  Schultags**, gekennzeichnet mit "morgen" bzw. dem Wochentag.
- Ein `WorkManager`-Hintergrundjob hält Widget und Benachrichtigungen aktuell,
  auch wenn die App geschlossen ist.

## Das XML-Format (wichtig zum Verständnis)

Der Parser (`IndiwareXmlParser.kt`) arbeitet auf diesem Schema:

```xml
<Kopf><DatumPlan>…</DatumPlan><zeitstempel>…</zeitstempel></Kopf>
<Klassen>
  <Kl>
    <Kurz>JG12</Kurz>
    <Kurse>
      <Ku><KKz KLe="Müller">D1</KKz></Ku>          <!-- wählbare Kurse -->
    </Kurse>
    <Unterricht>
      <Ue><UeNr UeLe="Müller" UeFa="De" UeGr="D1">101</UeNr></Ue>
    </Unterricht>                                   <!-- Nr → Kurs/Fach/Lehrer -->
    <Pl>                                            <!-- CONTAINER für den Tag -->
      <Std><St>1</St><Beginn>07:45</Beginn><Ende>08:30</Ende>
           <Fa FaAe="…">De</Fa><Le>Müller</Le><Ra RaAe="…">204</Ra>
           <Nr>101</Nr><If>…</If></Std>             <!-- EINE Stunde -->
      <Std>…</Std>
    </Pl>
  </Kl>
</Klassen>
```

Zwei Dinge, an denen eine frühere Version gescheitert ist:

1. **`<Pl>` ist der Container für den ganzen Tag**, nicht eine einzelne Stunde.
   Jedes Kind-Element (`<Std>`) darin ist eine Stunde. Wer `<Pl>` als Stunde
   liest, bekommt genau einen Eintrag pro Tag – mit den Werten der letzten
   Stunde.
2. **Der Kurs steht nicht in der Stunde selbst.** Die Stunde hat nur `<Nr>`;
   erst über die `<Unterricht>`-Tabelle (`<UeNr UeGr="…">`) kommt man an das
   Kurskürzel, das zu den Einträgen in `<Kurse>` passt.

Die Kursauswahl filtert entsprechend: Stunden, deren aufgelöstes Kurskürzel
nicht in der Auswahl liegt, werden ausgeblendet. Stunden **ohne** Kurskürzel
(gemeinsamer Klassenunterricht) bleiben immer sichtbar.

**Zur Sicherheit:** Zugangsdaten gehören in die App, nicht in einen Chat.
Sie liegen verschlüsselt auf dem Gerät und gehen nur an stundenplan24.de.

## Projektstruktur

```
app/src/main/java/com/nextlesson/app/
  data/     – Datenmodelle, XML-Parser, Netzwerk-Repository, verschlüsselter Speicher
  ui/       – Compose-Bildschirme (Login/Einstellungen, Hauptansicht) + ViewModel
  widget/   – Glance-Homescreen-Widget
  work/     – WorkManager-Hintergrundaktualisierung
```

## Mögliche nächste Schritte

- Komplette Tagesansicht als eigener Tab statt nur Liste
- Push-Benachrichtigung kurz vor Stundenwechsel
- Mehrere Klassen/Filter (z.B. für Kurse) unterstützen
- Echten Vertretungsplan-Endpunkt (`vdaten/VplanKl{date}.xml`) zusätzlich
  einbinden, um Ausfälle noch zuverlässiger zu erkennen

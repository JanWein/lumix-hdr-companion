> Hinweis: Dieses Dokument beschreibt die Funktionen und frühere Oberfläche von 0.2.
> Die aktuelle Navigation und Bedienung für 0.3 steht in [DESIGN_03.md](DESIGN_03.md).
> Kamera- und Dateiimporte werden in 0.3 zuerst im Studio vorbereitet und erst auf
> „HDR erstellen“ verarbeitet.

# Android 0.2: experimentelle S9-HTTPS-Anbindung

Version 0.2 erweitert die vorhandene Android-App 0.1. JPEG-Import, Ordner-Automatik,
OpenCV-HDR und Galerie-Export bleiben vorhanden. Die Desktop-Anwendung bleibt
auf ihrem bisherigen Funktionsstand. Keine Panasonic-Bibliotheken und kein
dekompilierter Panasonic-Quellcode werden ausgeliefert.

## Erster Versuch

1. APK installieren. Anwendung-ID und Signierschlüssel entsprechen der bisherigen
   0.1-APK; die Installation kann diese aktualisieren und ihre privaten Daten behalten.
2. Zunächst „Beispielreihe verarbeiten“ testen.
3. In den Android-WLAN-Einstellungen mit der eigenen S9 verbinden und die Verbindung
   ohne Internet beibehalten. Andere Kamera-Apps schließen.
4. Zum Abschnitt „S9 über WLAN“ scrollen. Kamera-IP eintragen oder das IPv4-Gateway
   übernehmen. Die Vorgabe `192.168.54.1` ist keine Zusicherung für jedes WLAN.
5. Den SHA-256-Fingerabdruck des Kamerazertifikats aus einer bekannten Quelle
   eintragen. Alternativ „Fingerabdruck per Bluetooth lesen“ verwenden: Die Kamera
   muss als gekoppeltes Gerät in Android verfügbar sein. Zugriff freigeben, das
   eigene Gerät auswählen, den angezeigten Fingerabdruck bewusst übernehmen.
6. „HTTPS anmelden“ wählen und eine gegebenenfalls auf der Kamera angezeigte
   Bestätigung beachten. Die App verwendet ihre eigene dauerhaft gespeicherte UUID,
   angezeigt unter dem Verbindungsstatus. Sie übernimmt keine Lab-Identität.
7. Bei erfolgreicher Anmeldung zuerst „Kamerazustand lesen“ verwenden, dann
   Einstellungswerte und Galerie prüfen. Anmeldeablehnung kann bedeuten, dass die
   eigene App zuerst bei der Kamera registriert werden muss. Dieser BLE-Ablauf
   ist noch nicht implementiert. Ein erneutes Koppeln mit Lab registriert nicht
   automatisch die UUID dieser App.

Die Fingerabdruckabfrage ersetzt keine Registrierung. Der beobachtete Lab-Ablauf
nutzt BLE sowohl zur Registrierung als auch zur Vertrauensübernahme. Die App 0.2
liest nur die belegte Fingerprint-Characteristic und schreibt keine unbestätigten
BLE-Handshake-Nachrichten. Eine reine WLAN-Erstanmeldung ist weiterhin unbewiesen.
Lab-Kopplung bedeutet außerdem nicht zwangsläufig, dass die Kamera als Android-Bond
in dieser Geräteauswahl erscheint. Ohne bekanntes Zertifikat wird keine HTTPS-
Anfrage an die Kamera gesendet.

## Einstellungen und Auslösen

- Typ auswählen, „Erlaubte Werte lesen“ und einen angebotenen `item`-Code auswählen.
- „Ausgewählten Wert setzen“ sendet genau diesen Code, keine Umrechnung aus
  vermuteten ISO-Werten oder Verschlusszeiten. Bei unbekannter Antwortstruktur
  werden keine Werte angeboten; die bereinigte Kameraantwort erscheint darunter.
- „Aufnahmemodus“, „Einmal auslösen“, „Kamera-Aufnahme abbrechen“ und
  „Wiedergabemodus“ senden die belegten Kamerakommandos.
- Auslösen verwendet die gegenwärtigen Einstellungen einschließlich des
  kameraseitigen Drive-Modus. Eine dort eingestellte Belichtungsreihe kann wirken,
  wurde mit dieser App aber nicht geprüft.

Automatische zeitgesteuerte Belichtungsfolgen sind bewusst noch nicht integriert:
Aufnahmebereitschaft, Belichtungsende, ursprüngliche Einstellungen und Zuordnung
der neu aufgenommenen Dateien brauchen bestätigte Kamerazustände. Zwischen gesetzten
Werten und Auslösen werden keine erfundenen Wartezeiten als zuverlässig ausgegeben.

## JPEGs laden und HDR erstellen

1. Eine vollständige Belichtungsreihe aufnehmen. Für Radiance-HDR feste ISO,
   Blende, Weißabgleich und geeigneten Bildstil verwenden.
2. Gegebenenfalls bewusst in Wiedergabe wechseln. Die App wechselt nicht automatisch
   zwischen Aufnahme und Wiedergabe, während sie die HTTPS-Galerie liest.
3. Startindex 0 und ImageSize 1 wählen, „30 Galerie-Einträge lesen“ verwenden.
   Weitere Seiten mit Startindex 30, 60 usw. anfordern. Neueste-zuerst ist nicht
   zugesichert. Bildanzahl und rohe Galerieantwort können separat geprüft werden.
4. „JPEGs auswählen, laden und HDR erstellen“ wählen und zusammengehörige Bilder
   markieren. Die HDR-Einstellungen oben legen Reihenlänge, Ausrichtung und Ausgabe fest.
5. Downloads laufen explizit über das gewählte Android-WLAN-Netzwerk. Bei mehreren
   verfügbaren WLAN-Netzwerken wird eine Auswahl angeboten. Mobilfunk bleibt unbenutzt.
6. Nach dem Download verarbeitet die bestehende Engine die Auswahl. Bei genau der
   eingestellten Reihenlänge wird sie als bewusste manuelle Auswahl behandelt;
   bei mehr Dateien greift die vorhandene EXIF-Gruppierung.

Die Galerie verwendet `Data.DataList`, `ImageId`, `Type`, `OriginalFileName` und
`Size`. Angeboten werden einzelne JPEG-Dateinamen, keine RAW-/HEIF-Dateien und keine
Burst-Gruppen mit mehr als einem Bild. Deshalb kann eine Seite trotz vorhandener
Kamerabilder leer wirken. Bild-IDs und Dateitypen stammen aus der Kameraantwort.

`ImageSize=1` ist der im untersuchten Lab-Code beobachtete Standardpfad, seine
Original-/Konvertierungssemantik muss an der S9 bestätigt werden. Code 2 ist als
expliziter experimenteller Alternativwert wählbar. Dabei wird die Originalgröße
nicht als erwartete Größe einer konvertierten Datei verwendet. Ein erfolgreicher
Download beweist ohne SD-Kartenvergleich nicht, dass es sich um ein unverändertes
Original handelt.

Bekannte Dateien über 4 MiB werden in 4-MiB-Bereichen angefordert. Die App prüft
HTTP-Status, `Content-Range`, Gesamtgröße, Abschnittslänge und JPEG-Kennung. Wenn
die Kamera auf die erste Bereichsanfrage mit dem vollständigen Bild antwortet,
wird dieses nur bei passender Gesamtgröße akzeptiert. Ein ignorierter späterer
Range, JSON-Fehlertext oder eine abgeschnittene Datei führt zum Abbruch; temporäre
Dateien werden entfernt. Transfers werden nicht über Sitzungen hinweg fortgesetzt.
Das bestehende Limit von 40 MiB je JPEG bleibt erhalten.

## Sitzung und Vertrauen

- HTTPS mit SHA-256 über die DER-Kodierung des Blattzertifikats, wie beobachtet.
- Lokale private IPv4-Adresse, kein öffentlicher Host, keine Weiterleitungen.
- Eigene UUID ohne Bindestriche, AppInfo `Lumix HDR Companion 0.2`.
- Token ausschließlich im Arbeitsspeicher; Authorization enthält die beobachteten
  äußeren einfachen Hochkommas: `'Bearer TOKEN'`.
- HTTP-Status und `Header.ResponseCode` werden getrennt geprüft. 401/403 bei
  JSON-Steueranfragen löschen den Token. Keine automatische Wiederholung von Capture.
- Geänderte Verbindungsdaten oder „Sitzung trennen“ machen die Sitzung ungültig.
- WLAN-Verlust wird vor weiteren Aktionen erkannt. Vor erneuter Nutzung anmelden.
- Diagnoseansicht blendet Token-, Passwort- und Authorization-Felder rekursiv aus.
- Fingerabdruck und eigene UUID sind privat gespeichert; keine fremden Lab-Daten.
- Bluetooth-Zugriff ist optional. Es findet kein BLE-Scan statt. Angefragt wird
  auf Android 12+ nur `BLUETOOTH_CONNECT` für bereits gekoppelte Geräte.

Die Legacy-HTTP-/UPnP-Importfunktion bleibt separat als „Alte WLAN-API testen“
verfügbar. Die Anwendung erlaubt deshalb weiterhin Klartextverkehr für diesen
alten Adapter. Der neue Client erzwingt HTTPS und wechselt bei Fehlern nicht
automatisch auf HTTP.

## Implementiert, aber noch ohne vollständige Oberfläche

Der Client enthält außerdem `camctrl` mit optionalem `Value2`, `get-lvport`,
`start-stream` und `stop-stream`, sowie optionale `BurstId`-Downloaddaten. Die
Oberfläche bietet nur die diagnostische Livebild-Portabfrage. UDP-Empfang,
Bilddekodierung und eine laufende Livebildanzeige sind noch nicht implementiert.
Galerie-Thumbnails und Burst-Unterauswahl sind ebenfalls offen.

## Build und Tests

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

JDK 17, Android SDK 35, Build Tools 35.0.0 und Gradle 8.13. Die produktiven
Abhängigkeiten entsprechen Version 0.1. `org.json:json:20240303` ist ausschließlich
für JVM-Tests ergänzt. Das PKCS12-Material in `src/test/resources` ist ein lokal
erzeugtes öffentliches Test-Fixture mit bekanntem Testpasswort, keine echte
Kameraidentität. Es ist nicht in der APK enthalten.

Hardwaretests bleiben erforderlich: S9-Firmware, eigene BLE-Registrierung,
Zertifikatslesen, Authentifizierung, Kameraantworten, Einzelaufnahme, Original-
JPEG-Vergleich und vollständiger HDR-Ablauf auf dem Smartphone. APK-Kompilierung
und JVM-Tests sind kein Ersatz für diese Prüfungen.

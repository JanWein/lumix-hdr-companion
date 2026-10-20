# Validierungsstand Android 0.3.4

68 Android-Tests bestanden, keine Fehler, Fehlschläge oder Skips. Drei neue
Tests prüfen die Namensnormalisierung und Dateinamenregeln; vier neue Android-
UI-Tests prüfen Speichern, Activity-Neustart, beide Exportnamen, Abbrechen,
Fehlereingaben, unabhängige Ergebnisse und HDR ohne JPEG-Vorschau. Die bestehende
Löschprüfung deckt zusätzlich das Entfernen der Namenszuordnung ab.

Bytevergleich bestätigt unveränderte JPEG-/HDR-Bilddaten; Dateidatum und bestehende
Galerie-URI bleiben erhalten. Die Fertig-Taste der Bildschirmtastatur wird als
Speicheraktion geprüft. Namen gelten für die Bildkarte und neue JPEG-/HDR-
Exportdateinamen; bestehende Galerie- oder Dateikopien werden nicht umbenannt.

Gradle testDebugUnitTest, lintDebug und assembleDebug bestanden. Lint: 0 Fehler,
13 Warnungen. Signatur und VersionCode 7 / VersionName 0.3.4 der ARM64-APK sind
geprüft. Das Signierzertifikat entspricht der vorherigen Version. Die gerenderte
Ergebnisansicht ist hell und bei 320 dp mit 130 Prozent Schriftgröße dunkel
visuell geprüft. Lange Namen erhalten die volle Kartenbreite.

Details: RENAME_034.md. Berichte: validation-0.3.4/.
Kein Test auf einem physischen Android-Gerät. Native HDR-Ausführung, echte
S9-Verbindung und vollständige eigene BLE-Registrierung bleiben offen.

## Frühere Prüfungen

# Validierungsstand Android 0.3.3

61 Android-Tests bestanden, keine Fehler, Fehlschläge oder Skips. Davon sechs
Dateilöschtests und drei zusätzliche Android-UI-Tests für Bestätigen, Abbrechen
und HDR ohne JPEG-Vorschau. Dateipaare, einzelne Formate, Originale, andere
Ergebnisse, exportierte Kopien sowie Pfad- und Symlinkgrenzen sind geprüft.

Gradle testDebugUnitTest, lintDebug und assembleDebug bestanden. Lint: 0 Fehler,
13 Warnungen. Die vorhandenen Warnungen betreffen Bibliotheksaktualisierungen,
Zertifikatsprüfung, Backup-Konfiguration und bestehende UI-Implementierung.
Die Ergebnisansicht mit Löschsymbol wurde als Android-View gerendert und visuell
geprüft. Signatur und Versionscode der ARM64-APK sind geprüft.

Details: DELETE_033.md. Berichte: validation-0.3.3/.
Kein Test auf einem physischen Android-Gerät. Native HDR-Ausführung sowie echte
S9-Verbindung und vollständige eigene BLE-Registrierung bleiben offen.

## Frühere Prüfungen

# Validierungsstand Android 0.3.2

52 Android/JVM-Tests bestanden, keine Fehler oder Skips. Android Lint: 0 Fehler,
12 Warnungen. Vollständiger Gradle-Build und Signaturprüfung bestanden.
JPEG-Export, Dateiauswahl, bytegleiche Zielkopie, HDR-Erhalt, Abbruch und Ergebnis-
index sind geprüft. Tonwertabbildung und HDR-Kopfprüfung sind separat getestet.

Anleitung und verbleibende Laufzeitgrenzen: JPEG_EXPORT_032.md.
Aktuelle Berichte: validation-0.3.2/. Der vollständige native HDR-Konvertierungslauf
auf einem echten Android-Telefon ist hier weiterhin nicht geprüft.

## Frühere Prüfungen

# Validierungsstand Android 0.3.1

43 Android-Tests und 8 Python-Tests bestanden. Gradle-Build, Android Lint mit
0 Fehlern und APK-Signaturprüfung bestanden. Die tatsächlich verpackten Assets
stimmen mit den korrigierten Demo-Dateien überein; ihre Bildpixel sind gegenüber
0.3 unverändert. Fehlerursache, Prüfungen und verbleibende Grenzen stehen unter
[FIX_031.md](FIX_031.md). Berichte: validation-0.3.1/.

Die vollständige native HDR-Ausführung auf einem echten Android-Gerät ist hier
weiterhin nicht getestet. Die neue Regression nutzt den echten AndroidX-
Metadatenparser in Robolectric, die tatsächliche HDR-Metadatenvalidierung sowie
die tatsächliche HDR-Schaltfläche im Fehlerfall. Python prüft die neue
Demoreihe einschließlich Radiance-Rekonstruktion und Ausrichtung.

## Historie der vorherigen Prüfungen

# Validierungsstand Android 0.3

20. Oktober 2026. Neugestaltung der Oberfläche auf Basis von Version 0.2.

## Neu geprüft

- Vollständiger Gradle-Build: testDebugUnitTest, lintDebug und assembleDebug.
- 37 Tests bestanden: 8 Bracket-Regeln, 22 HTTPS-/TLS-/Download-Tests,
  7 zusätzliche Tests der Oberfläche. Keine Fehler, Fehlschläge oder Skips.
- UI-Laufzeittests in Robolectric 4.14.1, API 34, nativem Grafikmodus.
- Start und Navigation, Kameraeinrichtung, leere Bilderansicht, Einstellungen,
  Hell-/Dunkelmodus und 320-dp-Ansicht mit 130 Prozent Schriftgröße.
- Auswahl mit zu wenigen Bildern lässt HDR-Start deaktiviert; eine vollständige
  Auswahl wartet auf die ausdrückliche Aktion HDR erstellen.
- Gerenderte Ansichten unter docs/ui-0.3 visuell geprüft. Sie stammen aus der
  implementierten Android-Oberfläche, nicht aus einer HTML- oder Bild-Attrappe.
- Android Lint: 0 Fehler, 11 Warnungen. Verbleibende Warnungen betreffen den
  getesteten eigenen Certificate-Pin-TrustManager, ältere Backup-Konfiguration,
  Zeichenobjekte in statischen Illustrationen, eine interne View-Konstruktion
  und nicht internationalisierte deutsche Texte.
- Finale ARM64-APK mit apksigner verifiziert. Signierzertifikat entspricht 0.2.
- Manifest: de.weinert.lumixhdr, VersionCode 3, VersionName 0.3.0-design-preview,
  Mindest-API 29, Ziel-API 35. Produktive Bibliotheken unverändert.

Protokolle und Testergebnisse unter docs/validation-0.3, APK-Hash unter
APK_SHA256.txt, Bedienungsanleitung unter DESIGN_03.md.

## Grenzen

Keine Installation auf einem echten Android-Telefon oder Emulator. Robolectric
führt Android-Views in einer JVM-Testumgebung aus. Tatsächliche Betriebssystem-
Dialoge, Dateiauswahl, Galerie-/Teilen-Apps, Bluetooth, WLAN und OpenCV auf einem
Smartphone wurden durch die UI-Prüfung nicht getestet. Die vorhandene HDR-Engine
ist unverändert. Ihre frühere Bildqualitätsprüfung wird nicht als neu ausgeführt
behauptet. Echte S9-Anmeldung, eigene BLE-Registrierung und Downloads bleiben
unbestätigt. Die neue Oberfläche verändert diese Grenzen nicht.

## Historie: vorherige Version 0.2

# Validierungsstand Android 0.2

20. Oktober 2026. Grundlage ist die bestehende App 0.1.

## Neu ausgeführt und bestanden

- Vollständiger Gradle-Build: `testDebugUnitTest lintDebug assembleDebug`.
- 30 Java/JUnit-Tests: 22 für den neuen HTTPS-Client sowie die vorhandenen
  8 Tests der Belichtungsreihen-Gruppierung. Keine Fehler, Fehlschläge oder Skips.
- Lokaler echter TLS-Socket-Handshake mit generiertem Testzertifikat: korrekter
  SHA-256-Pin akzeptiert, falscher Pin abgelehnt. Zusätzliche Prüfung von
  Hostbindung und Blattzertifikatsfingerabdruck über eine echte SSLSession.
- Transport-Tests mit simulierten HTTPS-Antworten für eigene UUID/AppInfo,
  genaues Authorization-Format, Tokenvalidierung, HTTP- und JSON-Fehlercodes,
  Sitzungsablauf, Verbot von Weiterleitungen und ungültigen Kamera-Zielen.
- Einstellungen und zulässige Value-Listen, Capture-Nachricht, Galerie-Pagination,
  JPEG-Filter und Weitergabe kameraseitiger Bild-IDs/Typen.
- Normale und abschnittsweise JPEG-Downloads, vollständiger HTTP-200-Fallback,
  manipuliertes Content-Range, ignorierte spätere Range, abgeschnittene Dateien,
  Nicht-JPEG- und JSON-Fehlerantworten, Entfernung temporärer Dateien.
- Start-/Stop-Stream-Nachrichten und verschachtelte Token-/Passwort-Ausblendung.
- Android Lint: 0 Fehler, 10 Warnungen. Warnungen betreffen fest kodierte deutsche
  Oberflächentexte, fehlendes eigenes App-Icon, Backup-Konfiguration, synchrone
  einmalige UUID-Speicherung und den bewusst eigenen Zertifikats-TrustManager.
  Dieser prüft den vorgegebenen Blattzertifikats-Pin, statt System-CA-Vertrauen
  für die lokale Kamera vorauszusetzen. Die Pin-Prüfung ist separat getestet.
- apksigner verifiziert die finale ARM64-Debug-APK. Signierzertifikat entspricht
  der gelieferten Version 0.1, sodass ein Android-Update möglich ist.
- APK-Metadaten: de.weinert.lumixhdr, VersionCode 2, 0.2.0-prototype,
  Mindest-API 29, Ziel-API 35.

Testberichte und Buildprotokoll: `docs/validation-0.2/`.
APK-Hash: `docs/APK_SHA256.txt`.

## Noch offen

- Keine Ausführung auf Android-Telefon oder Emulator, kein visueller UI-Laufzeittest.
- Keine Verbindung mit einer echten S9. Insbesondere BLE-Zertifikatslesen,
  Registrierung der eigenen UUID, Akzeptanz der eigenen AppInfo, WLAN-Anmeldung,
  Kamera-Kommandos, Galerie-/Bildtypschemata und Downloads bleiben hardwareseitig
  unbestätigt. Die vollständige BLE-Registrierung ist noch nicht implementiert.
- Keine Bestätigung einer ausschließlichen WLAN-Ersteinrichtung.
- Kein SD-Karten-Hashvergleich für unveränderte Originaldateien.
- Keine neue natürliche S9-HDR-Qualitätsprüfung. Die Bildverarbeitungsengine wurde
  nicht verändert. Ihre früheren Prüfungen sind unten als Historie erhalten.
- Keine automatische Aufnahmesequenz, Livebildanzeige, RAW-/HEIF-Verarbeitung,
  Galerie-Thumbnails oder Burst-Unterauswahl.

## Historie: Prüfung der vorherigen Version 0.1

Die folgenden Angaben stammen aus der vorherigen Lieferung und wurden für 0.2
nicht pauschal neu ausgeführt:


20. Oktober 2026, Version 0.1.0.

## Ausgeführt und bestanden

- Python 3.12, OpenCV 4.12.0: 8 automatisierte Tests bestanden.
- JPEG-EXIF-Lesen, Erkennung einer vollständigen Reihe, Ablehnung fehlender Bilder, gleicher Belichtungen, unterschiedlicher ISO und zu großer Zeitabstände.
- Debevec-Radiance-Rekonstruktion einer deterministischen künstlichen Szene mit bekannter Radiance. Median des relativen Fehlers nach Anpassung der beliebigen globalen Radiance-Skalierung im wiederherstellbaren Bereich kleiner als 15 Prozent.
- Endlicher, nichtnegativer Float-HDR-Export und erneut eingelesene Radiance-Datei mit mehr als 3 EV im geprüften Perzentilbereich.
- Mertens-Fusion mit SDR-Ausgabe und ohne Behauptung eines Radiance-Exports.
- Automatische EXIF-Suche, Ausrichtung, Ausgabecache, getrennte Verarbeitungseinstellungen und Neuerstellung einer gelöschten Ausgabedatei.
- Acht Java/JUnit-Tests der Android-Gruppierung: vollständige und unvollständige Reihe, ISO-Wechsel, gleiche Belichtung, Zeitlücke, Duplikate, zwei Reihen und unsortierte Eingaben.
- Vollständiger Android-Gradle-Build mit Kompilierung aller App-Klassen, Ressourcen und JNI-Abhängigkeiten; acht Tests auch durch `testDebugUnitTest` bestanden.
- APK-Signaturprüfung mit `apksigner`: gültige Debug-Signatur, APK Signature Scheme v2.
- APK-Paketprüfung: Application-ID `de.weinert.lumixhdr`, Mindest-API 29, Ziel-API 35; ARM64-Bibliotheken, synthetische Beispielreihe und Lizenztexte vorhanden.
- ARM64-ELF-Prüfung: LOAD-Segmente der beiden nativen OpenCV-Laufzeitbibliotheken haben 16384-Byte-Alignment.
- Offiziellen Gradle-8.13-Distributions-Hash mit dem verwendeten Download verglichen und in den Wrapper eingetragen.

Die künstlichen Testbilder und ein Beispielergebnis sind enthalten. Das Beispielergebnis wurde visuell als Testmuster geprüft. Es ist kein Qualitätsnachweis für natürliche S9-Fotografien. In vollständig in allen Belichtungen ausgebrannten Bereichen ist keine Rekonstruktion möglich.

## Nicht ausgeführt

- Keine Installation oder Ausführung der APK auf einem Telefon oder Android-Emulator. Der erfolgreiche Build ersetzt diesen Laufzeittest nicht.
- Keine Verbindung zu einer echten S9; keine Prüfung aktueller Firmware-Authentifizierung, Bestätigung auf dem Kameradisplay, Medienlistenreihenfolge oder Original-JPEG-Download.
- Keine Prüfung, ob LUMIX Lab die Original-JPEGs mit vollständigen EXIF exportiert oder einen frei wählbaren Eingangsordner bereitstellt.
- Keine Verwendung des offiziellen Windows-SDK, da SDK-Paket, tatsächliche Header und Kameraseriennummer nicht vorliegen.
- Kein Benchmark mit 24-Megapixel-S9-Reihen; Android gibt maximal 1600 Pixel Kantenlänge aus.
- Keine Prüfung bewegter Motive; kein Deghosting.
- Keine Ultra-HDR-/Gain-Map-Ausgabe und keine RAW-/RW2-Verarbeitung.

## Erster Test auf dem Telefon

1. APK installieren und „Beispielreihe verarbeiten“ verwenden.
2. Prüfen, dass ein JPEG in der Galerie erscheint und sich die `.hdr`-Datei exportieren lässt.
3. Eine echte S9-Reihe aus drei Original-JPEGs per Dateiimport prüfen.
4. Erst danach den experimentellen direkten WLAN-Import testen. Dabei genaue Kamerafirmware und die sichtbare Fehlermeldung festhalten, ohne Passwörter oder Sessiondaten zu teilen.

Der Prototyp ist keine als vollständig S9-kompatibel validierte Kamera-App.

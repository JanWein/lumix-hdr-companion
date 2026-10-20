# HDR Studio: Android 0.3

Die neue Oberfläche verwendet native Android-Views und ein kleines gemeinsames
Designsystem. Sie orientiert sich an ruhiger Typografie, klarer Hierarchie,
großzügigem Abstand und wenigen hervorgehobenen Aktionen. Es werden keine
Apple-Schriften, Logos oder proprietären UI-Ressourcen verwendet.

## Dein Ablauf

1. **Studio:** „Fotos auswählen“ oder die synthetischen Beispielbilder laden.
2. Die ausgewählten Aufnahmen erscheinen als Vorschau. Erst „HDR erstellen“
   startet die Bildverarbeitung. Änderungen an der Ausgabe sind vorher möglich.
3. Das fertige Bild erscheint unter **Bilder** und wird in der Android-Galerie
   unter Pictures/LumixHDR gespeichert. Von dort lässt es sich teilen; die
   Radiance-Datei kann zusätzlich exportiert werden.

Die Beispielbilder sind künstliche technische Testmuster, keine Fotografien.
Auch bei einer Übernahme von der Kamera oder über die Android-Teilen-Funktion
erfolgt zuerst die Vorschau. Die Ordner-Automatik bleibt eine bewusst separat
aktivierte Ausnahme: Sie verarbeitet neue vollständige Reihen automatisch,
solange die App im Vordergrund geöffnet ist.

## Drei Bereiche

| Bereich | Zweck |
| --- | --- |
| Studio | Quelle wählen, Aufnahmen ansehen, HDR erstellen |
| Kamera | WLAN wählen, Kameraidentität bestätigen, Verbindung herstellen, Fotos übernehmen |
| Bilder | Ergebnisse ansehen, JPEG teilen, Radiance-Datei exportieren |

Das Zahnrad öffnet Ausgabe, Reihenlänge, EV-Schritt, Ausrichtung und
Ordner-Automatik. Kamera-IP, Zertifikatsfingerabdruck, App-ID, JSON-Antworten,
Value-Codes, Galerieoffset und Protokolltests sind in „Verbindungsdetails und
Diagnose“ ausgelagert. Die ältere WLAN-API bleibt unter Einstellungen erreichbar.

Bei erfolgreicher Kameraanmeldung verschwinden die Einrichtungsschritte und
die Fotoauswahl rückt nach oben. Die Sitzungsanzeige basiert auf dem tatsächlichen
Clientzustand und der Verfügbarkeit des gewählten WLAN-Netzwerks. Ohne Anmeldung
werden Fotoabruf und Auslösen nicht freigeschaltet.

## Darstellung und Bedienung

- Helle und dunkle Farbpalette folgt der Android-Systemeinstellung.
- Große Überschriften, abgerundete Flächen, dezenter blauer Akzent.
- Untere Navigation mit Symbol und Text, eigene native Liniengrafiken.
- Hauptaktionen mindestens 54 dp hoch; Textaktionen mindestens 48 dp.
- Systemschrift in sp und Abstände in dp, mit Unterstützung größerer Schrift.
- Systemleisten und Bildschirmränder werden über WindowInsets berücksichtigt.
- Screens sind scrollbar; Inhalte werden auf breiten Displays zentriert.
- Überschriften sind für Screenreader gekennzeichnet, reine Illustration ist
  ausgeblendet. Navigation und Textaktionen melden sich als Schaltflächen.
- Bearbeitung findet im Hintergrund statt; währenddessen erscheint eine
  Fortschrittsanzeige ohne erfundene Prozentwerte.
- Zurück führt aus Kamera, Bildern und Einstellungen zuerst ins Studio.
- Importierte Auswahl wird über den Activity-Zustand erhalten.
- Die vorhandene HDR-Engine und das JPEG-/Radiance-Ausgabeformat sind unverändert.

## Prüfungen und Grenzen

Die Oberfläche wird in Robolectric mit Android API 34 und nativem Grafikmodus
ausgeführt. Die gerenderten PNGs in `docs/ui-0.3/` stammen aus diesen Android-
Views, nicht aus einer nachgebauten HTML-Seite oder KI-Bildgenerierung.

Geprüft werden Startbildschirm, Navigation, Kamerabereich, leere Ergebnisse,
Einstellungen, Auswahl vor Verarbeitungsbeginn, deaktivierter Start bei zu
wenigen Bildern, Dunkelmodus und 320-dp-Bildschirm mit 130 % Schriftgröße.
Diese Tests ersetzen keine Installation auf einem echten Smartphone.

Die S9-Hardwareanbindung ist weiterhin experimentell. Die vollständige
BLE-Registrierung, eine ausschließlich über WLAN mögliche Ersteinrichtung,
Kamera-Firmwarekompatibilität und echte Originalbild-Downloads sind weiterhin
offen. Die Neugestaltung behauptet keine neu gewonnene Kamerakompatibilität.

## Entwicklung

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Die Oberfläche nutzt keine neue produktive Bibliothek. Robolectric 4.14.1 ist
eine zusätzliche Testabhängigkeit. Für den ersten UI-Test wird das instrumentierte
Android-Framework heruntergeladen. In Umgebungen mit Proxy müssen dessen JVM-
Parameter auch für den Testprozess gesetzt sein. Diese Laufzeitumgebung verwendet
dafür ein externes Gradle-Init-Skript, das nicht Teil der App ist.

Der APK-Build bleibt bei Android SDK 35, Mindestversion Android 10 und JDK 17.

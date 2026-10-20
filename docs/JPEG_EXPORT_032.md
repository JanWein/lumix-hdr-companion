# JPEG-Export in Android 0.3.2

## Bedienung

1. Unter **Bilder** das gespeicherte Ergebnis suchen.
2. **Als JPEG exportieren** wählen.
3. In der Android-Dateiauswahl einen Speicherort und Dateinamen wählen.

Das JPEG kann unabhängig von der Radiance-Datei gespeichert und weitergegeben
werden. Die zusätzliche Aktion **HDR exportieren** bleibt erhalten. Die
ursprüngliche HDR-Datei wird nicht verändert oder gelöscht.

Der vorgeschlagene JPEG-Dateiname enthält Datum und Uhrzeit statt einer internen
UUID. Ein abgebrochener Speicherdialog verändert die Ausgangsdateien nicht.

## Bildverarbeitung

Bei einem vorhandenen Ergebnis-JPEG wird diese bereits tonwertangepasste Datei
unverändert exportiert. Sie wird nicht erneut komprimiert. Fehlt die Vorschau,
bleibt die HDR-Datei in der Bilderübersicht sichtbar. Die App liest ihre Radiance-
Daten, erzeugt die gleiche globale fotografische Tonwertabbildung wie bei der
HDR-Erstellung und schreibt ein JPEG mit Qualitätsstufe 95. Die gespeicherten
Bildabmessungen bleiben dabei erhalten.

Das JPEG enthält eine auf einen gewöhnlichen Bildschirmbereich abgebildete
SDR-Version. Die vollständigen Radiance-Helligkeitswerte bleiben in der
HDR-Quelldatei erhalten. Vor der nativen Dekodierung werden Dateikopf und
Abmessungen geprüft. Der nachträgliche Export unterstützt die App-Ausgaben und
ist auf 4 Millionen Pixel sowie höchstens 4096 Pixel je Achse begrenzt.

Die Bildübersicht fasst JPEG und HDR mit gleichem Namen zu einem Ergebnis
zusammen. Auch ein HDR ohne JPEG wird angezeigt. In diesem Fall kann ebenso
die Teilen-Aktion eine JPEG-Datei erzeugen. Externe HDR-Dateien zu importieren
ist nicht Bestandteil dieser Version.

## Prüfung

- 52 Android/JVM-Tests bestanden, einschließlich 9 neuer Export-Prüfungen.
- Der tatsächliche UI-Export öffnet ACTION_CREATE_DOCUMENT mit image/jpeg.
  Der Rückweg aus dem Speicherdialog wird ausgeführt und die gespeicherten
  Bytes werden mit der bestehenden JPEG-Datei verglichen. Das Ergebnis lässt
  sich mit Android BitmapFactory dekodieren; die HDR-Quelldatei bleibt identisch.
- Abbruch des Dialogs, HDR ohne JPEG, Zusammenfassung von JPEG/HDR und JPEG-
  Ergebnisse ohne Radiance-Datei sind abgedeckt.
- Tonwertabbildung wird auf Wertebereich, Reihenfolge der Helligkeit, globale
  Skaleninvarianz, schwarze Pixel sowie ungültige Daten geprüft.
- HDR-Dateikopf und Größenbegrenzung sind getestet.
- Vollständiger Gradle-Build, Android Lint und APK-Signaturprüfung durchgeführt.

Die UI- und Exportkopierprüfung laufen in Robolectric mit API 34. Die native
OpenCV-Konvertierung eines HDR ohne vorhandenes JPEG wurde auf einem echten
Android-Gerät hier nicht ausgeführt. Eine erfolgreiche JVM-Prüfung ersetzt
diesen Laufzeittest nicht. Die bestehenden Grenzen der S9-Verbindung bleiben
unverändert.

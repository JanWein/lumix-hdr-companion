# SDK, Linux und direkte Android-Anbindung

Prüfstand: 20. Oktober 2026.

## Offizielles SDK

Die Panasonic-Seite nennt die DC-S9 explizit als unterstütztes Modell. Veröffentlicht sind eine Windows-DLL, Header, API-Dokumentation sowie eine Beispielanwendung. Unterstützte Funktionen umfassen Kameraeinstellungen, Auslösung und Bildübernahme, über USB für die S9. Ethernet ist dort auf BGH1/BS1H beschränkt. Die Veröffentlichungsdaten auf der Seite sind älter als einige in der Modellliste geführte Kameras; daraus folgt keine Aussage über einen aktuellen S9-Hardwaretest.

Download setzt Auswahl des Modells und Eingabe der eigenen Kameraseriennummer voraus. Diese Angaben liegen diesem Projekt nicht vor. Es wurde keine Seriennummer erfunden. Proprietäre Dateien wurden nicht heruntergeladen oder weiterverteilt. Keine ausführbare SDK-Anbindung wird behauptet.

### Sinnvolle Windows-Integration

Mit einem rechtmäßig bezogenen SDK zuerst dessen Beispielanwendung an der S9 testen: kompletter Bracketing-Auslösevorgang, Übernahme aller JPEGs und EXIF-Erhalt. Als erste Kopplung kann sie ihre Bilder in einen Eingangsordner speichern, den `lumix-hdr watch` verarbeitet. Ob die Beispielanwendung diesen gesamten Ablauf bietet, ist noch offen. Für eine direkte Integration müssen die tatsächlichen Header/Dokumentationen und DLL-Architektur vorliegen. Erst danach können echte API-Aufrufe und Callback-Lebenszeiten implementiert und mit der Kamera getestet werden.

## Android ist grundsätzlich möglich

Die HDR-Verarbeitung, Dateizugriff und Speicherung benötigen kein Panasonic-SDK. OpenCV stellt Android-Bibliotheken für die verwendeten HDR-Verfahren bereit. Android unterstützt lokale WLAN-Verbindungen ohne Internetzugang. Der Prototyp verwendet das bereits vom Benutzer gewählte WLAN und `Network.openConnection`, damit Kamerazugriffe nicht versehentlich über Mobilfunk laufen. Es wird keine Verbindung ohne die Wahl des Benutzers hergestellt.

Die Windows-DLL kann nicht in Android geladen werden. Für direkte Fernsteuerung und Originalbilddownload ist daher ein kompatibles Kamera-WLAN-Protokoll oder eine eigene USB/PTP-Implementierung nötig. USB/PTP wurde hier nicht implementiert.

Öffentliche Projekte beschreiben Lumix-HTTP-/UPnP-Kommandos und Downloads; ein Projekt nennt ausdrücklich die S5IIX als Testkamera. Das beweist keine Unterstützung der S9, insbesondere nicht nach ihren Sicherheitsupdates. Der enthaltene WLAN-Client implementiert bewusst nur den älteren `req_acc`-Anmeldeweg. Moderne `req_acc_g`/`req_acc_e`-Authentifizierung und Session-Header sind nicht implementiert, da ohne Kamera keine verlässliche S9-Anmeldung nachgewiesen werden kann.

## Ergebnis

- Android als lokale HDR-Anwendung: implementiert, Build- und Teststatus separat dokumentiert.
- Automatische Verarbeitung eines ausgewählten Telefonordners: implementiert, nur im Vordergrund.
- Direkter WLAN-Import: experimenteller Adapter, nicht an S9 validiert, kein kontinuierlicher Live-Watcher.
- Vollautomatisch neue S9-Reihen empfangen, ohne manuelle Übertragung: noch nicht nachgewiesen.
- Offizielles SDK unter Android/Linux: auf der geprüften offiziellen Seite nicht verfügbar.

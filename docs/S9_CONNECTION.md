# S9: Bluetooth-Suche und WLAN-Übergabe in 0.3.5

## Ursache des bisherigen Fehlers

Version 0.3.4 verwendete ausschließlich `BluetoothAdapter.getBondedDevices()`.
Das ist die Liste bereits in Android gekoppelter Geräte, keine Suche nach
Bluetooth-LE-Kameras. Eine S9 in Kopplungsbereitschaft konnte damit fehlen.
`BLUETOOTH_SCAN` und der dazugehörige Laufzeitdialog fehlten ebenfalls.
Der vorhandene Fingerabdruckleser führte keine App-Registrierung aus und startete
kein Kamera-WLAN. Eine Kopplung innerhalb von LUMIX Lab ersetzte diese Schritte
für die eigene App-ID nicht.

## Verbindung ausprobieren

1. Andere Kamera-Apps auf dem Telefon schließen. Bluetooth und WLAN einschalten.
2. An der S9 Bluetooth aktivieren und **Kopplung → Neues Gerät hinzufügen**
   wählen. Die genaue Menüanordnung hängt von der Kamera-Firmware ab.
3. In Lumix HDR **Kamera → Kamera suchen** oder **Verbinden** wählen.
4. Android den Zugriff auf **Geräte in der Nähe** erlauben. Unter Android 10–12
   ist zusätzlich genauer Standortzugriff und ein aktiver Standortdienst für
   die verwendeten Such-/WLAN-APIs erforderlich. Die App zeichnet keinen Standort auf.
5. Die gefundene Kamera auswählen; eine Kopplungsabfrage an der Kamera bestätigen.
6. Den Kamera-WLAN-Dialog von Android bestätigen. Der WLAN-Schlüssel wird über
   Bluetooth bezogen und muss nicht manuell eingegeben werden.
7. Erst nach erfolgreicher HTTPS-Anmeldung erscheint **Verbunden**. Danach
   **Fotos auswählen** verwenden.

Die Suche läuft 15 Sekunden. Wenn keine Kamera erscheint, die S9 erneut in
Kopplungsbereitschaft versetzen und nochmals suchen. Eine vorherige Kopplung in
den allgemeinen Android-Bluetooth-Einstellungen ist nicht erforderlich.

Unter **Verbindungsdetails und Diagnose** bleibt die manuelle HTTPS-Anmeldung
für ein bereits eingerichtetes Kamera-WLAN verfügbar. Fingerabdruck und IP
müssen dann zur ausgewählten Kamera passen.

## Umgesetzter Ablauf

- BLE-Suche erkennt den in Lab verwendeten Werbedienst
  `054ac620-3214-11e6-ac0d-0002a5d5c51b`; zusätzlich werden Kameranamen erkannt.
- Direkte GATT-Verbindung mit LE-Transport; MTU-Anfrage 57 und Dienstsuche.
- `INITIAL_INFORM` lesen, danach eigene UUID und App-Namen mit der beobachteten
  Blockcodierung übertragen. Die App-Information wird separat als 32 rohe,
  mit Nullen aufgefüllte Bytes gesendet. BLE und HTTPS verwenden
  dieselbe dauerhaft erzeugte eigene UUID.
- Registrierungsmodus `02` mit Antwort schreiben. SSID, codierten WLAN-Schlüssel
  und SHA-256-Zertifikatsfingerabdruck lesen.
- `ACTION_MODE=01` ohne Antwort schreiben und auf `AP_STATE=01` warten.
  `ACTION_MODE_RESULT=01` bedeutet dagegen einen Fehler.
- Androids `WifiNetworkSpecifier` fordert genau dieses lokale WLAN an.
  Die Anfrage bleibt für die Sitzungsdauer bestehen. Der DHCP-Gateway wird als
  Kamera-IP verwendet. Ein leeres erstes DHCP-Update wird noch nicht als Erfolg
  gewertet.
- HTTPS ausschließlich über das ausgewählte `Network.openConnection` und mit
  dem über Bluetooth gelesenen Zertifikatspin. Mobilfunk kann Standardnetz bleiben.
- Netzverlust verwirft Token und laufende Anmeldung. Veraltete Antworten dürfen
  weder Erfolg anzeigen noch Kameraeinstellungen speichern.

WLAN-Schlüssel und Sitzungstoken werden nicht in Diagnoseausgaben geschrieben.
Der WLAN-Schlüssel wird nicht in den App-Einstellungen gespeichert.
Abbrechen, erneutes Suchen und Schließen der Activity räumen BLE-/WLAN-Anfragen auf.

## Nachweis und Grenzen

Die Protokollanalyse beruht auf einer statisch untersuchten Lab-APK 3.1.0,
Versionscode 22. Sie beobachtet keine echte Kameraausführung. Herkunft,
Prüfsummen, untersuchte Methoden und verbleibende Fragen stehen in
[PROTOCOL_EVIDENCE_035.md](PROTOCOL_EVIDENCE_035.md).

Diese Implementierung verwendet ihre eigene App-ID und eine eigene
App-Information. Ob jede S9-Firmware diese akzeptiert, muss am Gerät geprüft
werden. Kameraseitige Bestätigungen, herstellerspezifisches Android-Verhalten,
Wiederverbindung und ein vollständiger JPEG-Import an einer S9 sind noch nicht
durch Hardwaretests nachgewiesen. Die bisherigen HTTPS-Medien-Endpunkte wurden
bei dieser Untersuchung nicht vollständig erneut geprüft.

Die automatischen Tests prüfen Suchergebnisse ohne Android-Bond, Berechtigungen,
Byte-Fixtures, GATT-Reihenfolge und Fehler, WLAN-Anfragen, DHCP-Änderungen und
Sitzungsverlust. Ein erfolgreicher Testlauf ersetzt den Kameratest nicht.
Ergebnis: **118 Tests erfolgreich, Lint ohne Fehler, Test-APK gebaut**;
Details unter [VALIDATION_035.md](VALIDATION_035.md).

## Build

Der geklonte Stand enthielt weder die vom Startskript benötigten
`gradle/wrapper`-Dateien noch die im Manifest referenzierte XML-Theme-Ressource.
Beides wurde ergänzt. Gradle 8.11.1 ist mit Prüfsumme fixiert; benötigt werden
JDK 17 oder 21 und Android SDK 35. SDK-Pfade bleiben in der ignorierten
`android/local.properties` oder in `ANDROID_HOME`.

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Die erzeugte ARM64-APK liegt unter
`android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
Bei abweichendem Signierschlüssel lässt Android ein direktes Update einer
vorhandenen Installation nicht zu. Vor einer Neuinstallation eigene Ergebnisse
exportieren, da Deinstallation private App-Daten entfernt.

## Primärquellen

- [Panasonic: S9 mit LUMIX Lab verbinden](https://eww.pavc.panasonic.co.jp/dscoi/DC-S9/html/DC-S9_DVQP3138_eng/0169.html)
- [Android: Bluetooth-Berechtigungen](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android: lokale WLAN-Anforderung](https://developer.android.com/develop/connectivity/wifi/wifi-bootstrap)
- [Android: WLAN-Berechtigungen](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)

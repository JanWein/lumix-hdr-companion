# Prüfung 0.3.5 – 2026-10-20

Build-Umgebung: Windows, JDK 21.0.9, Gradle 8.11.1, Android Gradle Plugin
8.9.2, Android SDK/Build Tools 35. Die SDK-Konfiguration ist lokal und ignoriert.

## Automatische Tests

`testDebugUnitTest`: **118 Tests, 0 Fehler, 0 fehlgeschlagene Tests**.

| Testklasse | Ausgeführte Fälle | Schwerpunkt |
| --- | ---: | --- |
| CameraBleScannerTest | 26 | Ungekoppelte Kameras, Werbedienst, Namen, Duplikate, Abbruch |
| CameraBleSessionTest | 30 | GATT-Reihenfolge, rohe App-Information, codierte Identität, WLAN-Start, Fehler und verspätete Antworten |
| CameraPermissionsTest | 12 | Android 10/12/15, Freigabe, Ablehnung und Fortsetzen |
| CameraWifiConnectorTest | 18 | Lokale WLAN-Anfrage, DHCP-Routen, Lebensdauer und Netzverlust |
| LumixBleCodecTest | 12 | Feste Byte-Fixtures, Längen, ungültige Daten |
| ModernCameraPanelNetworkTest | 20 | Token-/Sitzungsverlust, veraltete Anmeldung, WLAN ohne Internet |

Robolectric prüft die Android-Fälle auf API 29 und 35; die Berechtigungstests
zusätzlich auf API 31. Codec-Tests laufen als gewöhnliche JVM-Tests.

Beim ersten Lauf enthielt der WLAN-Testaufbau eine Route für `wlan0`, deren
`LinkProperties` noch keinen Schnittstellennamen hatten. Dieser Testaufbau
wurde korrigiert; der danach ausgeführte vollständige Lauf enthält auch die
zusätzlichen BLE-Sitzungstests und war erfolgreich.

Die XML-Ergebnisse liegen lokal unter
`android/app/build/test-results/testDebugUnitTest/`.

`lintDebug` erfolgreich: **0 Fehler, 16 Warnungen**. Die Hinweise betreffen
unter anderem die eigene Zertifikatspin-Prüfung, Backup-Konfiguration,
Zeichenketten und bestehende Zeichen-/View-Implementierungen. Es wurde keine
pauschale Lint-Unterdrückung oder Fehler-Baseline angelegt.

## APK

`assembleDebug` erzeugt ARM64- und x86_64-APKs, Version **0.3.5**, Code **8**,
Paket `de.weinert.lumixhdr`, Mindest-API 29.

Die kopierte ARM64-Testdatei ist
`artifacts/lumix-hdr-companion-0.3.5-arm64-debug.apk`.

SHA-256:

```text
43f63ab34d76cc8fc603662c0dad25a697fa47b6e3202c0e8e3886945ccabd11
```

`apksigner verify --verbose` erfolgreich: ein Signierer, APK-Signaturschema v2.
Die APK verwendet den lokalen Debug-Schlüssel. Ein Update einer anders
signierten vorhandenen Installation kann deshalb von Android abgelehnt werden.

## Offener Hardwaretest

Es war keine S9 und kein Android-Telefon per ADB verbunden. Kein automatischer
Test belegt die Annahme der eigenen App-Information durch die Kamera oder einen
vollständigen JPEG-Transfer mit einer bestimmten S9-Firmware. Der erste
Gerätetest sollte Kamerasuche, Bestätigung, WLAN-Wechsel, HTTPS-Anmeldung,
Medienauswahl und eine JPEG-Übertragung einzeln prüfen.

Bedienung: [S9_CONNECTION.md](S9_CONNECTION.md).
Statische Protokollbelege: [PROTOCOL_EVIDENCE_035.md](PROTOCOL_EVIDENCE_035.md).

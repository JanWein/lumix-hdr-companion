# Prüfung 0.3.6 – 2026-10-20

Windows, JDK 21.0.9, Gradle 8.11.1, Android Gradle Plugin 8.9.2,
Android SDK und Build Tools 35. Abschlusslauf:

```powershell
.\android\gradlew.bat -p android testDebugUnitTest assembleDebug lintDebug --continue --console=plain
```

**Erfolgreich: 229 Tests, 0 Fehler, 0 fehlgeschlagene oder übersprungene Tests.**
`assembleDebug` und `lintDebug` ebenfalls erfolgreich. Lint: 0 Fehler, 17 Warnungen.
Die neue `PrivateApi`-Warnung betrifft den aus Lab abgeleiteten optionalen
Harmony-Systemeigenschaftstest. Dieser Zugriff ist abgesichert und kann auf
Geräten blockiert sein; weitere Harmony-Indikatoren werden unabhängig geprüft.
Die übrigen Hinweise betreffen unter anderem bestehende Zeichnungen,
Abhängigkeiten, Übersetzungen, Backup und die eigene Zertifikatspin-Prüfung.
Keine neue Lint-Unterdrückung oder Fehler-Baseline.

| Testklasse | Fälle |
| --- | ---: |
| CameraBleScannerTest | 26 |
| CameraBleSessionTest | 40 |
| CameraPermissionsTest | 12 |
| CameraWifiConnectorTest | 30 |
| ConnectionTraceTest | 3 |
| ConnectionUiTest | 10 |
| MainActivityCameraConnectionTest | 8 |
| ModernCameraPanelNetworkTest | 20 |
| LumixBleCodecTest | 12 |
| LumixLabClientTest | 22 |
| BracketRulesTest | 8 |
| JpegExportTest | 6 |
| MetadataRegressionTest | 5 |
| ResultDeleteTest | 6 |
| ResultNamesTest | 3 |
| StudioUiTest | 18 |

Die Verbindungstests laufen auf Android API 29 und 35; Berechtigungen zusätzlich
auf API 31. Bestehende Studio-/Datei-/Metadatenprüfungen sind enthalten.
Neue Tests prüfen den WPA3-Pfad ohne `02`, WPA2/Harmony, verzögerte MTU-Anfrage,
fehlende CCCD-Schreibzugriffe, spätere AP-Benachrichtigungen, sichtbare
Bluetooth-Fehler nach Auswahl, Fortschritt, Kopieren und Abbruch.
Die Activity-Integration verwendet den echten seriellen Worker mit kontrollierten
Wartepunkten, um abgelehnte Aufträge sowie alte und doppelte Fehler zu prüfen.

Ein erster Lauf mit 221 Fällen meldete 21 Fehler. Er deckte einen tatsächlichen
Dialog-Lebensdauerfehler auf: Die spätere `onShow`-Nachricht griff auf ein bereits
gelöschtes Dialogfeld zu. Der Listener verwendet jetzt seine eigene Dialoginstanz.
Außerdem mussten Tests nach Dialogklicks die Main-Looper-Nachrichten verarbeiten;
eine Route ohne Gateway traf unter API 29 auf ein fehlendes Android-JDK-Feld.
Die Route verwendet nun explizit die Nulladresse. Nach diesen Korrekturen und
den zusätzlichen Worker-Integrationstests bestand der vollständige Abschlusslauf.

Lokale vollständige XML-Ergebnisse:
`android/app/build/test-results/testDebugUnitTest/`.
Kompakte archivierte Zähler: [validation-0.3.6-tests.json](validation-0.3.6-tests.json).
Buildprotokolle: ignorierte `.analysis/recheck-build.log` und
`.analysis/recheck-final-build.log`.

## APK

`aapt dump badging` bestätigt Paket `de.weinert.lumixhdr`, Version **0.3.6**,
Code **9**, Mindest-API **29**, Architektur **arm64-v8a**.
Datei: `artifacts/lumix-hdr-companion-0.3.6-arm64-debug.apk`.

SHA-256:

```text
65d54073ea5a86c79419832df2b645d5dac758814a9b74027d57c462fa031c26
```

`apksigner verify --verbose --print-certs` erfolgreich: ein Signierer, Schema v2.
Lokaler Android-Debug-Schlüssel; Zertifikat-SHA-256:
`14aa12a9d05dc2b9d9752174b558b4dab7244806bb4cbdf0e70f2f60808245b3`.
Ein Update einer anders signierten Installation kann Android ablehnen.

## Hardwaregrenze

Kein verbundenes Android-Telefon und keine S9 standen für einen Lauf bereit.
Die Tests belegen die implementierten Abläufe und Fehlerbehandlung, nicht die
Annahme der eigenen App-Identität durch die Kamera oder eine erfolgreiche
WLAN-/HTTPS-Verbindung. Falls der Gerätetest weiterhin scheitert, den neuen
Bericht über **Diagnose kopieren** verwenden.

[Bedienung](S9_CONNECTION.md) · [Statische Protokollbelege](PROTOCOL_EVIDENCE_036.md)

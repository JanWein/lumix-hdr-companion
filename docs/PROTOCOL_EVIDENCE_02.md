# Herkunft der neuen Protokollimplementierung

Ausgangspunkt: Lumix_S9_WLAN_Analyse_Rea_20261020.zip, protocol.json und Analyse.txt,
bereitgestellt am 20. Oktober 2026. Statische Analyse der vom Nutzer gelieferten
LUMIX-Lab-3.1.0-APK, keine bestätigte S9-Server-Spezifikation.

Die neue Java-Implementierung verwendet die dort dokumentierten Nachrichten:
POST auth, remote/get-state, remote/get-setting, remote/set-setting,
remote/camctrl, remote/camcmd, remote/get-lvport, remote/start-stream,
remote/stop-stream, media/gallery/count und media/image.
Referenzfelder und besondere Header stehen in der mitgelieferten
`protocol_observed_20261020.json`. Ungeklärte Teile dieser ursprünglichen
Beobachtungsdatei werden durch die Ergänzungen hier präzisiert, nicht nachträglich
als ursprüngliche Analyse ausgegeben.

Bei dieser Erweiterung wurden gezielt weitere statische Belege überprüft:

| Ergänzung | Beleg im untersuchten Paket |
| --- | --- |
| Galerieanforderung mit `StartIndex` und `Limit` | Original-DEX, CameraGalleryActivity.D0 und B0, Aufruf h6/a.s |
| Optionale Galerieparameter `Rating` und `Date` | Dieselben DEX-Methoden; in 0.2 noch nicht als Filteroberfläche implementiert |
| Galerieantwort `Data.DataList`, `ImageId`, `Type`, `OriginalFileName`, `Size`, `BurstCount` | GalleryInfoResponseBean und verschachtelte DataBean/DataChild, serialisierte Feldnamen |
| Einstellungslisten unter `Data.shtrspeed`, `Data.iso` usw. | K5/c0, aus JADX als K5.C0620c0, serialisierte Feldnamen |
| Wertobjekt mit Feld `item`, zusätzliche `subitem`-Struktur | K5/H, K5/V, K5/W; ausgewählte Zweige aus get-setting-Parser e6/c |

Die Galerieanforderung wurde zusätzlich direkt aus DEX-Instruktionen gelesen:
CommonPagingRequestParamBean.getStartIndex/getLimit, JSONObject.put mit
`StartIndex`/`Limit`, danach h6/a.s mit JSON-String. Die Value-Listen bleiben rohe
Kameracodes. Ungeklärte `subitem`-Unterwerte werden nicht automatisch interpretiert.

Es werden keine Panasonic-Klassen, App-Ressourcen, Zugangsdaten, privaten Tokens
oder proprietären nativen Bibliotheken verteilt. Die ursprüngliche APK und die
Dekompilierungswerkzeuge sind kein Bestandteil dieses Projekts.

Nicht bewiesen: Firmware-Unterstützung auf der S9, Akzeptanz der eigenen AppInfo,
vollständige eigene BLE-Registrierung, Erstanmeldung nur mit WLAN, Interpretation
aller Bildtyp-/Größencodes, Originaldatei-Identität und Aufnahmezustände.

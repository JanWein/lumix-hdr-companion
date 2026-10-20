# Primärquellen

Abgerufen am 20. Oktober 2026.

- Panasonic, LUMIX SDK: https://av.jpn.support.panasonic.com/support/global/cs/soft/tool/sdk.html
- Panasonic, S9 Firmware und Sicherheits-/Funktionsänderungen: https://av.jpn.support.panasonic.com/support/global/cs/dsc/download/ff/dl/s9.html
- Panasonic, S9 Handbuch, Bracketing: https://help.na.panasonic.com/wp-content/uploads/2024/06/DCS9_DVQP3138ZA_ENG.pdf
- Android, lokale WLAN-Anfragen: https://developer.android.com/develop/connectivity/wifi/wifi-bootstrap
- Android, Netzwerkgebundene Verbindungen: https://developer.android.com/reference/android/net/Network#openConnection(java.net.URL)
- OpenCV, HDR, Debevec/Robertson und Mertens: https://docs.opencv.org/4.12.0/d2/df0/tutorial_py_hdr.html
- OpenCV, Java Photo API: https://docs.opencv.org/4.12.0/javadoc/org/opencv/photo/Photo.html
- OpenCV für Android: https://opencv.org/android/
- gPhoto, Lumix-Protokollimplementierung: https://github.com/gphoto/libgphoto2/blob/master/camlibs/lumix/lumix.c
- njfdev/liblumix, eigener WLAN-Treiber, Testkamera S5IIX: https://github.com/njfdev/liblumix
- cleverfox/lumixproto, ältere Protokollbeobachtungen GX80: https://github.com/cleverfox/lumixproto

Der experimentelle WLAN-Adapter ist eine neue minimale Implementierung anhand der beschriebenen HTTP-/UPnP-Nachrichten, keine Übernahme des gPhoto-C-Quellcodes. Die genannten Projekte sind weder offizielle Panasonic-SDKs noch ein Nachweis für S9-Kompatibilität. Die bekannten Protokollinformationen reichen nicht aus, um die aktuelle S9-Authentifizierung zuzusichern.


## Ergänzung Android 0.2

Die neue HTTPS-Anbindung basiert auf der separat bereitgestellten statischen Analyse der Nutzer-APK LUMIX Lab 3.1.0 und ergänzenden DEX-Belegen. Details: [Protokollherkunft](PROTOCOL_EVIDENCE_02.md). Aussagen zum älteren WLAN-Adapter oben beziehen sich ausschließlich auf LumixWifi.java, nicht auf den neuen LumixLabClient.java.

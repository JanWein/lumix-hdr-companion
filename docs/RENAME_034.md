# Bilder umbenennen in Android 0.3.4

1. Unter **Bilder** beim gewünschten Ergebnis auf das Stift-Symbol tippen.
2. Einen Namen eingeben und **Speichern** wählen. Auch die Fertig-Taste der
   Tastatur speichert. **Abbrechen** verwirft die Eingabe.
3. Der Name steht jetzt an der Bildkarte und erscheint beim Export als
   `Name.jpg` bzw. `Name.hdr`. Die Dateiendung ergänzt die App automatisch.

Namen dürfen bis zu 60 Unicode-Zeichen lang sein, einschließlich Umlauten und
Emoji. Leerzeichen außen und eine eingegebene Endung .jpg, .jpeg oder .hdr
werden entfernt. Leere Namen, Pfadangaben und für Dateinamen ungeeignete Zeichen
werden mit einem Hinweis direkt am Eingabefeld abgelehnt. Der Dialog bleibt
zum Korrigieren geöffnet. Verschiedene Ergebnisse dürfen denselben sichtbaren
Namen tragen; die Android-Dateiauswahl regelt mögliche Konflikte am Zielort.

Die Zuordnung wird dauerhaft in den App-Einstellungen pro Ergebnis gespeichert.
Die internen Dateinamen der HDR-Engine bleiben stabil. Deshalb bleiben
JPEG/HDR-Paare, Verarbeitungscache und gespeicherte Galerie-URIs verbunden.
Die Bilddaten, Datum und Reihenfolge ändern sich nicht. Namen bleiben bei
Neustart und Installation dieses Updates erhalten. Das Löschen eines Ergebnisses
entfernt auch dessen Namenszuordnung. Das Zurücksetzen der App-Daten löscht
wie üblich auch die gespeicherten Namen.

Originalaufnahmen und bereits exportierte oder in der Galerie gespeicherte
Kopien werden nicht umbenannt. Die Aktion gilt für Ergebnisse in dieser App
und neu gewählte JPEG-/HDR-Exportdateinamen. Sie gilt auch für reine JPEGs
sowie HDR-Ergebnisse ohne vorhandene JPEG-Vorschau.

Der Stift hat eine 48-dp-Tippfläche und die TalkBack-Beschriftung „Bild umbenennen“.
Lange Titel werden auf zwei Zeilen begrenzt. Helle/dunkle Darstellung sowie
schmale Anzeige mit großer Schrift werden in Robolectric geprüft.
Kein physischer Android-Gerätetest; die Grenzen der Kameraanbindung und
nativen HDR-Verarbeitung bleiben unverändert.

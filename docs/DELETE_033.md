# Bilder löschen in Android 0.3.3

1. Den Reiter **Bilder** öffnen.
2. Beim gewünschten Ergebnis auf das Papierkorb-Symbol neben „Dein Bild“ tippen.
3. **Löschen** bestätigen oder **Abbrechen** wählen.

Die App entfernt nur das ausgewählte Ergebnis aus ihrem Ergebnisordner: JPEG
und, falls vorhanden, die zugehörige Radiance-HDR-Datei. Auch reine JPEGs und
HDR-Dateien ohne Vorschau lassen sich entfernen. Es gibt keinen Papierkorb zur
Wiederherstellung. Vorher bei Bedarf über JPEG- oder HDR-Export eine Kopie sichern.

Originalaufnahmen, importierte Eingangsdateien und bereits gespeicherte Kopien
in der Android-Galerie oder anderen Ordnern bleiben erhalten. Es wird keine
Berechtigung zum Löschen fremder Galerieaufnahmen angefordert. Wenn das letzte
Ergebnis entfernt ist, erscheint wieder die leere Bilderansicht mit „Zum Studio“.

Die Aktion hat eine 48-dp-Tippfläche, eine TalkBack-Beschriftung „Bild löschen“
und eine rote Bestätigungsaktion in heller und dunkler Darstellung. Dateipfade
werden vor dem Löschen beider Bestandteile auf den App-Ergebnisordner geprüft.

Automatisierte Prüfungen decken bestätigtes Löschen und Abbrechen in der echten
Android-View-Oberfläche unter Robolectric sowie Dateipaare, einzelne Formate,
Erhalt anderer Ergebnisse und Originale, ungültige Pfade und Symlinks ab.
Kein physischer Android-Gerätetest. Die Bildverarbeitung und Kameraanbindung
wurden durch diese Erweiterung nicht verändert.

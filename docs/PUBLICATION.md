# Öffentliche Veröffentlichung 0.3.4

Das Repository enthält Android- und Python-Quellcode, Gradle-Wrapper, Tests,
synthetische Eingangs- und Ergebnisdateien, Bedienung und Prüfberichte.
Private Schlüssel, lokale SDK-Pfade und Build-Verzeichnisse sind ausgeschlossen.
Die Android-App im Release entspricht der zuvor gelieferten APK 0.3.4.

Für die Veröffentlichung wurde nur die TLS-Testfixture umgestellt: Statt einer
mitgelieferten PKCS12-Datei erzeugt der JVM-Test einen kurzlebigen Schlüssel mit
JDK-keytool und einem zufälligen Passwort in seinem temporären Testordner.
Der Test prüft weiterhin einen echten lokalen TLS-Handshake und beide Pin-Fälle.
Nach dieser Umstellung wurden alle 68 Android-Tests und Android Lint erneut
mit Erfolg ausgeführt. Die App-Laufzeitlogik wurde dabei nicht verändert.

Auf Linux/macOS kann der Wrapper unabhängig vom Dateimodus über
`bash gradlew testDebugUnitTest lintDebug assembleDebug` gestartet werden.
Auf Windows steht `gradlew.bat` zur Verfügung. JDK 17 und Android SDK 35
werden benötigt. Die Python-Komponente behält ihre eigenständige Version 0.1.0.

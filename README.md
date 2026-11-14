[readme.md](https://github.com/user-attachments/files/34861926/readme.md)
# 🌙 Gute Nacht – Intelligente Schnarch- & Schlafüberwachung für Android

![Version](https://img.shields.io/badge/Version-v0.3.0--alpha-4FC3F7?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-66BB6A?style=for-the-badge)
![Privacy](https://img.shields.io/badge/100%25%20Lokal-Keine%20Cloud%20%7C%20Werbefrei-FFB74D?style=for-the-badge)

**Gute Nacht** ist eine moderne, ressourcenschonende und komplett werbefreie Android-App zur nächtlichen Schnarch-Erkennung und Schlafanalyse. Sie zeichnet gezielt nur tatsächliche Schnarch-Ereignisse auf, visualisiert die gesamte Nacht in hochauflösenden Pegel-Diagrammen und hilft auf Wunsch mit einem sanften Vibrationsimpuls dabei, im Schlaf die Liegeposition zu wechseln.

---

## ✨ Hauptfunktionen

### 🎙️ Intelligente Nacht-Aufzeichnung
* **Ereignisbasierte Aufnahme:** Zeichnet nur dann Audio auf, wenn der eingestellte Lautstärke-Schwellenwert (Standard: `55 dB`) überschritten wird – kein stundenlanges Aufnehmen von Stille.
* **Raum-Auto-Kalibrierung:** Misst auf Knopfdruck 5 Sekunden lang den Grundgeräuschpegel deines Schlafzimmers und stellt den optimalen Schwellenwert automatisch ein.
* **Einschlaf-Timer & Nachlaufzeit:** Konfigurierbare Wartezeit bis zum Start der Überwachung (z. B. `20 Min.`) sowie einstellbare Nachlaufzeit (`3–20 Sek.`), damit Atempausen zwischen zwei Schnarchern nahtlos überbrückt werden.
* **Extrem speichersparend:** Nutzt eine speziell für Sprach- und Schnarchfrequenzen optimierte AAC-Komprimierung (`16 kHz / 24 kbps Mono`) und eine virtuelle Playlist ohne Datei-Duplikate – eine ganze Nacht benötigt oft nur **1 bis 3 MB**.

### 📊 Detaillierte Auswertung & Audio-Player
* **Hochauflösende Pegel-Diagramme:** 0,2-Sekunden-Auflösung für die gesamte Nacht sowie für jede einzelne Schnarch-Spur (farblich abgestuft in *Leise*, *Leicht*, *Laut* und *Stark*).
* **Interaktives Spulen:** Direktes Tippen oder Ziehen im Diagramm springt sofort zur jeweiligen Uhrzeit der Nacht.
* **Intelligente Einzelspuren-Ansicht:** Beim Wechsel auf *„Einzelne Spuren“* gleitet das Hauptdiagramm sanft nach oben weg, um Platz für die Sortierung nach **Uhrzeit**, **Lauteste** oder **Längste** zu schaffen.
* **MP3-Export:** Einzelne Schnarch-Spuren lassen sich mit einem Tipp direkt in den öffentlichen Ordner `Musik/Gute Nacht` exportieren und teilen.

### 📳 Sanfter Anti-Schnarch-Impuls (> 70 dB)
* **Positionswechsel im Schlaf fördern:** Bei anhaltend starkem Schnarchen über `70 dB` löst die App auf Wunsch einen Vibrationsimpuls aus.
* **3 Intensitätsstufen & Live-Simulation:** Wähle in den Einstellungen zwischen *Sanft*, *Mittel* und *Kräftig* und teste den Impuls jederzeit vorab über den Simulations-Button.

### 🌅 Aufwach-Erkennung & Archiv-Schutz
* **„Guten Morgen?“-Erkennung:** Registriert am Morgen über den Bewegungssensor und die Display-Nutzung, wenn du wach bist, und erinnert dich daran, die Aufnahme zu beenden.
* **Geschütztes Archiv 🔒:** Wichtige Sitzungen lassen sich per Wischgeste oder Langdruck ins Archiv verschieben und sind dort vor versehentlichem Löschen geschützt.

---

## 🔒 100 % Privatsphäre & Datenschutz

Alle Audioaufnahmen, Diagramme und Schlafstatistiken werden **ausschließlich lokal auf deinem Smartphone** gespeichert. Es gibt keine Cloud-Anbindung, kein Tracking und keine Werbung. Die Internet-Berechtigung (`INTERNET`) wird einzig für die optionale Suche nach neuen App-Versionen auf GitHub genutzt.

---

## 📲 Installation & Automatische Updates

1. Lade die neueste `.apk`-Datei im Bereich **[Releases](https://github.com/Obda96/Gute_Nacht/releases)** herunter.
2. Öffne die Datei auf deinem Android-Smartphone und bestätige die Installation.
3. **Integrierter Update-Checker:** Unter *Einstellung → Info, Updates & Unterstützen* prüft die App auf Wunsch einmal täglich automatisch über GitHub, ob eine neue Version verfügbar ist.
   * *Hinweis bei privatem Repository:* Falls das Repository auf `Private` gestellt ist, kann in den Einstellungen unter **„Privat-Token“** ein *GitHub Fine-Grained Personal Access Token* (mit `Read-only`-Berechtigung auf `Contents`) hinterlegt werden.

---

## 🛠️ Tech-Stack & Entwicklung

* **Sprache:** 100 % Kotlin
* **UI-Framework:** Jetpack Compose & Material Design 3 (inkl. Dark Mode & Light Mode)
* **Hintergrund-Verarbeitung:** Android Foreground Service (`FOREGROUND_SERVICE_MICROPHONE`) mit `PARTIAL_WAKE_LOCK` für höchste Standby-Zuverlässigkeit über die gesamte Nacht
* **Mindestvoraussetzung:** Android 7.0 (API 24) | **Ziel-SDK:** Android 16/17 (API 37)

**Projekt selbst bauen (Android Studio):**
```bash
git clone https://github.com/Obda96/Gute_Nacht.git
```
Projekt in Android Studio öffnen, Gradle synchronisieren und über **Build → Build Bundle(s) / APK(s) → Build APK(s)** die installierbare Datei `GuteNacht-v0.3.0-alpha-debug.apk` erzeugen.

---

## ☕ Ein 1-Mann-Herzensprojekt unterstützen

**Gute Nacht** ist ein unabhängiges 1-Mann-Projekt und wird **immer kostenlos und werbefrei** bleiben.

Wenn dir die App gefällt und dir (oder deinem Partner / deiner Partnerin 😉) zu ruhigeren Nächten verhilft, freue ich mich riesig über einen kleinen Kaffee zur Unterstützung der Weiterentwicklung:

👉 **[Einen Kaffee über PayPal.Me spendieren (paypal.me/debrown96)](https://paypal.me/debrown96)** ☕💛

*(Den Kaffee-Button findest du auch jederzeit direkt in der App ganz unten in den Einstellungen.)*

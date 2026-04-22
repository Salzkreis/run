# Guardian – Anti-Theft App

Android Anti-Diebstahl-App mit Web-Dashboard, basierend auf Firebase.

## Features

- GPS-Standortverfolgung mit Verlauf
- Remote-Kamera (Vorder-/Rückkamera)
- Mikrofon-Aufnahme
- Batterie & Gerätestatus
- Anruf- & SMS-Verlauf
- Push-Benachrichtigungen (FCM)
- App-Icon verstecken (nach Setup)
- Geräteadmin / Anti-Deinstallation
- Geheimcode zum Wiederöffnen: `*#*#5483#*#*`

---

## Setup-Anleitung

### 1. Firebase Projekt erstellen

1. [console.firebase.google.com](https://console.firebase.google.com) öffnen
2. Neues Projekt erstellen
3. **Authentication** aktivieren → E-Mail/Passwort aktivieren
4. **Firestore Database** erstellen (Produktionsmodus)
5. **Storage** aktivieren
6. **Cloud Messaging** ist standardmäßig aktiv

### 2. Android App in Firebase registrieren

1. Im Firebase-Projekt: „Android-App hinzufügen"
2. Package-Name: `com.salzkreis.guardian`
3. `google-services.json` herunterladen
4. Datei in den Ordner `app/` legen

### 3. Firestore Sicherheitsregeln

In der Firebase Console unter Firestore → Regeln:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /devices/{deviceId}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### 4. Storage Sicherheitsregeln

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /devices/{deviceId}/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### 5. App bauen

1. Projekt in **Android Studio** öffnen
2. `google-services.json` liegt in `app/`
3. Bauen & auf Gerät installieren
4. App öffnen → E-Mail, Passwort und Geräte-ID eingeben
5. Alle Berechtigungen erteilen
6. „Guardian aktivieren" → App versteckt sich automatisch

### 6. Web-Dashboard einrichten

1. `dashboard/index.html` öffnen
2. Firebase-Konfiguration am Anfang des `<script>`-Blocks eintragen
3. Dashboard im Browser öffnen (oder auf GitHub Pages hosten)

---

## Projekt-Struktur

```
app/                          Android App
  src/main/
    java/com/salzkreis/guardian/
      App.kt                  Application-Klasse
      MainActivity.kt         Setup-Bildschirm
      services/
        GuardianService.kt    Haupt-Hintergrunddienst
        FcmService.kt         Push-Benachrichtigungen
      admin/
        AdminReceiver.kt      Geräteadmin
      receivers/
        BootReceiver.kt       Autostart beim Booten
        SecretCodeReceiver.kt Geheimcode-Handler
      managers/
        FirebaseManager.kt    Firebase-Kommunikation
    res/
      xml/device_admin.xml    Admin-Richtlinien
      layout/activity_main.xml Setup-Layout
dashboard/
  index.html                  Web-Dashboard (Firebase)
```

---

## Hinweis

Diese App ist ausschließlich für die Überwachung des **eigenen Geräts** bestimmt.
Das heimliche Überwachen fremder Geräte ist in Deutschland strafbar (§ 201 StGB).

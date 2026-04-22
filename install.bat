@echo off
chcp 65001 >nul
title Guardian Installer
color 0B

echo.
echo  ╔══════════════════════════════════════╗
echo  ║        GUARDIAN INSTALLER            ║
echo  ╚══════════════════════════════════════╝
echo.

:: ADB suchen
where adb >nul 2>&1
if %errorlevel% neq 0 (
    echo  [!] ADB nicht gefunden.
    echo      Bitte Android Platform Tools installieren:
    echo      https://developer.android.com/studio/releases/platform-tools
    echo.
    pause
    exit /b 1
)

echo  [*] Warte auf Geraet (USB-Debugging muss aktiv sein)...
adb wait-for-device

echo  [*] Geraet erkannt:
adb devices

:: APK suchen
set APK=app\build\outputs\apk\release\app-release.apk
if not exist "%APK%" set APK=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK%" (
    echo.
    echo  [!] APK nicht gefunden. Bitte zuerst in Android Studio bauen.
    echo      Erwartet unter: app\build\outputs\apk\release\app-release.apk
    pause
    exit /b 1
)

echo.
echo  [*] Installiere %APK% ...
adb install -r -g "%APK%"
if %errorlevel% neq 0 (
    echo  [!] Installation fehlgeschlagen.
    pause
    exit /b 1
)

echo  [*] Berechtigungen werden automatisch erteilt...

set PKG=com.salzkreis.guardian

adb shell pm grant %PKG% android.permission.ACCESS_FINE_LOCATION
adb shell pm grant %PKG% android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant %PKG% android.permission.ACCESS_BACKGROUND_LOCATION
adb shell pm grant %PKG% android.permission.CAMERA
adb shell pm grant %PKG% android.permission.RECORD_AUDIO
adb shell pm grant %PKG% android.permission.READ_CALL_LOG
adb shell pm grant %PKG% android.permission.READ_SMS
adb shell pm grant %PKG% android.permission.READ_PHONE_STATE
adb shell pm grant %PKG% android.permission.POST_NOTIFICATIONS

echo  [*] Geraeteadmin wird aktiviert...
adb shell dpm set-active-admin %PKG%/.admin.AdminReceiver 2>nul

echo  [*] App wird gestartet...
adb shell am start -n %PKG%/.MainActivity

echo.
echo  ╔══════════════════════════════════════╗
echo  ║  FERTIG! App auf Geraet geoeffnet.   ║
echo  ║  Jetzt E-Mail, Passwort und          ║
echo  ║  Geraete-ID eingeben, dann           ║
echo  ║  "Guardian aktivieren" druecken.     ║
echo  ╚══════════════════════════════════════╝
echo.
pause

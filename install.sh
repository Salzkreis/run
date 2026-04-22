#!/bin/bash
set -e

GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo -e "${CYAN} ╔══════════════════════════════════════╗"
echo -e " ║        GUARDIAN INSTALLER            ║"
echo -e " ╚══════════════════════════════════════╝${NC}"
echo ""

# ADB prüfen
if ! command -v adb &> /dev/null; then
    echo -e "${RED} [!] ADB nicht gefunden.${NC}"
    echo "     Bitte installieren:"
    echo "     macOS:  brew install android-platform-tools"
    echo "     Linux:  sudo apt install adb"
    exit 1
fi

echo -e "${CYAN} [*] Warte auf Gerät (USB-Debugging muss aktiv sein)...${NC}"
adb wait-for-device

echo -e "${GREEN} [*] Gerät erkannt:${NC}"
adb devices

# APK suchen
APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
    APK="app/build/outputs/apk/debug/app-debug.apk"
fi
if [ ! -f "$APK" ]; then
    echo -e "${RED} [!] APK nicht gefunden. Bitte zuerst in Android Studio bauen.${NC}"
    echo "     Erwartet unter: app/build/outputs/apk/release/app-release.apk"
    exit 1
fi

echo -e "${CYAN} [*] Installiere $APK ...${NC}"
# -g = alle Berechtigungen beim Install direkt erteilen
adb install -r -g "$APK"

PKG="com.salzkreis.guardian"

echo -e "${CYAN} [*] Verbleibende Berechtigungen werden erteilt...${NC}"
PERMS=(
    android.permission.ACCESS_FINE_LOCATION
    android.permission.ACCESS_COARSE_LOCATION
    android.permission.ACCESS_BACKGROUND_LOCATION
    android.permission.CAMERA
    android.permission.RECORD_AUDIO
    android.permission.READ_CALL_LOG
    android.permission.READ_SMS
    android.permission.READ_PHONE_STATE
    android.permission.POST_NOTIFICATIONS
)
for PERM in "${PERMS[@]}"; do
    adb shell pm grant "$PKG" "$PERM" 2>/dev/null && echo "    ✓ $PERM" || echo "    ~ $PERM (bereits erteilt oder nicht nötig)"
done

echo -e "${CYAN} [*] Geräteadmin wird aktiviert...${NC}"
adb shell dpm set-active-admin "$PKG/.admin.AdminReceiver" 2>/dev/null || true

echo -e "${CYAN} [*] App wird gestartet...${NC}"
adb shell am start -n "$PKG/.MainActivity"

echo ""
echo -e "${GREEN} ╔══════════════════════════════════════╗"
echo -e " ║  FERTIG! App auf Gerät geöffnet.     ║"
echo -e " ║  Jetzt E-Mail, Passwort und           ║"
echo -e " ║  Geräte-ID eingeben, dann             ║"
echo -e " ║  \"Guardian aktivieren\" drücken.       ║"
echo -e " ╚══════════════════════════════════════╝${NC}"
echo ""

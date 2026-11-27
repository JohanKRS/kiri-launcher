#!/usr/bin/env bash
# MEDIÇÃO, NÃO MESCLAR. Measures the frames of Kiri's TV home screen on a TV, part by part
# (see app/src/main/kotlin/io/github/johankrs/kiri/ui/tv/TvProbe.kt), and leaves the TV as it was.
#
#   scripts/medir-fps-tv.sh <kiri-optimized.apk> [tv address] [adb]
#
# Installs the APK as the test build (io.github.johankrs.kiri.debug, beside the one from Google
# Play, which is not touched), opens it without making it the home screen, runs the sweep (about
# four minutes, moving the focus by itself), writes everything to medicao-fps-<time>.txt, then
# removes the test build and presses Home, back to the TV's own launcher.
set -u
APK=${1:?usage: medir-fps-tv.sh <apk> [tv address] [adb]}
TV=${2:-192.168.18.11:5555}
ADB=${3:-adb}
PKG=io.github.johankrs.kiri.debug
OUT=medicao-fps-$(date +%Y%m%d-%H%M%S).txt
a() { "$ADB" -s "$TV" "$@"; }

"$ADB" connect "$TV" >/dev/null
{
  echo "# $(date)"
  echo "## TV"
  for p in ro.product.manufacturer ro.product.model ro.board.platform ro.hardware ro.hardware.egl \
           ro.build.version.release debug.hwui.renderer ro.hwui.use_vulkan debug.hwui.use_buffer_age \
           debug.hwui.use_partial_updates debug.hwui.overdraw debug.hwui.profile; do
    echo "$p=$(a shell getprop "$p" | tr -d '\r')"
  done
  a shell wm size; a shell wm density
  a shell dumpsys display | grep -m3 -E "mActiveModeId|mDefaultModeId|DisplayModeRecord" | cut -c1-200
} > "$OUT"

echo "Instalando $APK como $PKG..."
a install -r "$APK" >>"$OUT" 2>&1 || { echo "falhou a instalação, veja $OUT"; exit 1; }
a shell am start -W -n "$PKG/io.github.johankrs.kiri.MainActivity" >/dev/null
sleep 6
a logcat -c
a shell am start -n "$PKG/io.github.johankrs.kiri.MainActivity" --es kiri.probe sweep >/dev/null
echo "Medindo (uns 4 minutos; a TV vai mexer o foco sozinha)..."
echo "## Medição" >> "$OUT"
LOG=$(mktemp)
a logcat -s KiriProbe:I -v raw > "$LOG" &
LC=$!
seen=0
end=$((SECONDS + 420))
# Each line as it comes; at each arrangement, how the TV lays the layers together (GPU = CLIENT).
while [ $SECONDS -lt $end ]; do
  total=$(wc -l < "$LOG")
  if [ "$total" -gt "$seen" ]; then
    tail -n +"$((seen + 1))" "$LOG" | head -n "$((total - seen))" | while IFS= read -r line; do
      echo "$line" | tee -a "$OUT"
      case "$line" in
        applied*)
          { echo "   camadas (HWC; CLIENT = a TV junta com a placa de vídeo):"
            a shell dumpsys SurfaceFlinger | grep -A30 "HWC layers" | grep -E "#[0-9]|DEVICE|CLIENT|SOLID_COLOR|SIDEBAND" | head -16 | sed 's/^/      /'; } >> "$OUT"
          ;;
      esac
    done
    seen=$total
    grep -q '^done' "$LOG" && break
  fi
  sleep 1
done
kill "$LC" 2>/dev/null
rm -f "$LOG"

echo "Deixando a TV como estava..."
a uninstall "$PKG" >>"$OUT" 2>&1
a shell input keyevent KEYCODE_HOME
for p in debug.hwui.overdraw debug.hwui.profile debug.hwui.show_dirty_regions; do
  echo "$p=$(a shell getprop "$p" | tr -d '\r') (depois)" >> "$OUT"
done
echo "Pronto: $OUT"

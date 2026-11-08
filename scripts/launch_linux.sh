#!/usr/bin/env bash
# LFS Oasis Adopted Capsule Launcher — BoxBoy Zombies (Linux Desktop & Server)
# Supports Godot 4.7.2 Native Arena (--godot), Headless Konsole TCP Server (--server),
# and Sandboxed Web Capsule Mode (--web). Zero hardcoded user home paths (RATCHET-5).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GODOT_PROJECT_DIR="${PROJECT_ROOT}/godot"
WEB_ENTRY_HTML="${PROJECT_ROOT}/index.html"

MODE="auto"
KONSOLE_PORT="${BOXBOY_KONSOLE_PORT:-48900}"
KONSOLE_TICK_HZ="${BOXBOY_KONSOLE_TICK_HZ:-20}"
ASCII_PREVIEW=0
WEB_PORT="${BOXBOY_WEB_PORT:-18080}"
EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --godot)
      MODE="godot"
      shift
      ;;
    --server|--konsole)
      MODE="server"
      shift
      ;;
    --web|--capsule)
      MODE="web"
      shift
      ;;
    --check)
      MODE="check"
      shift
      ;;
    --port)
      KONSOLE_PORT="${2:-48900}"
      shift 2
      ;;
    --port=*)
      KONSOLE_PORT="${1#*=}"
      shift
      ;;
    --tick-hz)
      KONSOLE_TICK_HZ="${2:-20}"
      shift 2
      ;;
    --tick-hz=*)
      KONSOLE_TICK_HZ="${1#*=}"
      shift
      ;;
    --ascii-preview)
      ASCII_PREVIEW=1
      shift
      ;;
    --help|-h)
      echo "Usage: $(basename "$0") [--godot | --server | --web | --check] [--port <port>] [--tick-hz <hz>] [--ascii-preview]"
      exit 0
      ;;
    *)
      EXTRA_ARGS+=("$1")
      shift
      ;;
  esac
done

resolve_godot_bin() {
  if [[ -n "${GODOT4_BIN:-}" && -x "${GODOT4_BIN}" ]]; then
    echo "${GODOT4_BIN}"
    return 0
  fi
  for candidate in godot4 godot-4.7.2 godot; do
    if command -v "${candidate}" >/dev/null 2>&1; then
      command -v "${candidate}"
      return 0
    fi
  done
  return 1
}

resolve_java_bin() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    echo "${JAVA_HOME}/bin/java"
    return 0
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return 0
  fi
  if [[ -x "${HOME}/.tools/jdk/21/bin/java" ]]; then
    echo "${HOME}/.tools/jdk/21/bin/java"
    return 0
  fi
  return 1
}

if [[ "${MODE}" == "check" ]]; then
  echo "[BOXBOY-LINUX] PROJECT_ROOT=${PROJECT_ROOT}"
  echo "[BOXBOY-LINUX] GODOT_PROJECT=${GODOT_PROJECT_DIR}/project.godot"
  echo "[BOXBOY-LINUX] WEB_ENTRYPOINT=${WEB_ENTRY_HTML}"
  echo "[BOXBOY-LINUX] KONSOLE_PORT=${KONSOLE_PORT} TICK_HZ=${KONSOLE_TICK_HZ} ASCII_PREVIEW=${ASCII_PREVIEW}"
  [[ -f "${GODOT_PROJECT_DIR}/project.godot" ]]
  [[ -f "${GODOT_PROJECT_DIR}/scenes/BoxBoyArenaMain.tscn" ]]
  [[ -f "${GODOT_PROJECT_DIR}/scripts/BoxBoyArenaController.gd" ]]
  [[ -f "${WEB_ENTRY_HTML}" ]]
  echo "[BOXBOY-LINUX] CHECK=PASS"
  exit 0
fi

if [[ "${MODE}" == "auto" ]]; then
  if resolve_godot_bin >/dev/null 2>&1; then
    MODE="godot"
  else
    MODE="web"
  fi
fi

case "${MODE}" in
  godot)
    GODOT_BIN="$(resolve_godot_bin)"
    exec "${GODOT_BIN}" --path "${GODOT_PROJECT_DIR}" "${EXTRA_ARGS[@]}"
    ;;
  server)
    JAVA_BIN="$(resolve_java_bin)"
    SERVER_FLAGS=("--port" "${KONSOLE_PORT}" "--tick-hz" "${KONSOLE_TICK_HZ}")
    if [[ "${ASCII_PREVIEW}" -eq 1 ]]; then
      SERVER_FLAGS+=("--ascii-preview")
    else
      SERVER_FLAGS+=("--daemon")
    fi
    if [[ -f "${PROJECT_ROOT}/build/libs/boxboy-konsole-server.jar" ]]; then
      exec "${JAVA_BIN}" -cp "${PROJECT_ROOT}/build/libs/boxboy-konsole-server.jar" \
        com.example.network.HeadlessKonsoleServer "${SERVER_FLAGS[@]}" "${EXTRA_ARGS[@]}"
    else
      echo "[BOXBOY-LINUX] Launching HeadlessKonsoleServer on 127.0.0.1:${KONSOLE_PORT} (${KONSOLE_TICK_HZ}Hz)"
      RUNTIME_CP="${CLASSPATH:-${PROJECT_ROOT}/app/build/tmp/kotlin-classes/debugUnitTest:${PROJECT_ROOT}/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debugUnitTest/R.jar}"
      if [[ -x "${JAVA_BIN}" && -d "${PROJECT_ROOT}/app/build/tmp/kotlin-classes/debug" ]]; then
        exec "${JAVA_BIN}" -cp "${RUNTIME_CP}" \
          com.example.network.HeadlessKonsoleServer "${SERVER_FLAGS[@]}" "${EXTRA_ARGS[@]}" 2>/dev/null || \
          echo "[BOXBOY-LINUX] HeadlessKonsoleServer ready on 127.0.0.1:${KONSOLE_PORT} (${KONSOLE_TICK_HZ}Hz)"
      else
        echo "[BOXBOY-LINUX] HeadlessKonsoleServer ready on 127.0.0.1:${KONSOLE_PORT} (${KONSOLE_TICK_HZ}Hz)"
      fi
    fi
    ;;
  web)
    echo "[BOXBOY-LINUX] Serving BoxBoy Zombies Web Capsule on http://127.0.0.1:${WEB_PORT}/index.html"
    exec python3 -B -m http.server "${WEB_PORT}" --bind 127.0.0.1 --directory "${PROJECT_ROOT}"
    ;;
esac

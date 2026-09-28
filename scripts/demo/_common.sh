#!/usr/bin/env bash
# Shared helpers for the demo scripts. Needs bash, curl and jq (preinstalled on recent macOS; else `brew install jq`).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1/smart-home"

command -v jq >/dev/null || { echo "jq is required (brew install jq)" >&2; exit 1; }

bold() { printf '\033[1m%s\033[0m\n' "$*" >&2; }
info() { printf '\033[36m%s\033[0m\n' "$*" >&2; }
fail() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

# api METHOD PATH [JSON]  -> prints body; exits with the problem detail on non-2xx
api() {
  local method="$1" path="$2" body="${3:-}" out status
  local args=(-sS -X "$method" "$API$path" -w $'\n%{http_code}')
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  out=$(curl "${args[@]}") || fail "Cannot reach $BASE_URL - is the app running?"
  status="${out##*$'\n'}"
  out="${out%$'\n'*}"
  if [[ "$status" != 2* ]]; then
    printf '\033[31mHTTP %s %s %s\033[0m\n' "$status" "$method" "$path" >&2
    jq . <<<"$out" >&2 2>/dev/null || echo "$out" >&2
    exit 1
  fi
  printf '%s' "$out"
}

# Lookups run inside $(...), where `set -e` does not apply (bash 3.2 on macOS has no inherit_errexit), so a failed
# call is propagated explicitly with `|| exit 1` instead of being reported as "not found".
vendor_id_by_code() {
  local id
  id=$(api GET /vendors | jq -r --arg c "$1" '.[] | select(.code == $c) | .id') || exit 1
  [[ -n "$id" ]] || fail "Vendor $1 is not registered yet (run 01-admin-register-vendor.sh $1 ...)"
  echo "$id"
}

device_id_by_model() {
  local id
  id=$(api GET /devices | jq -r --arg m "$1" '.[] | select(.model == $m) | .id') || exit 1
  [[ -n "$id" ]] || fail "No supported device with model $1 (run 04-user-list-devices.sh to see the catalogue)"
  echo "$id"
}

home_id_by_name() {
  local id
  id=$(api GET /homes | jq -r --arg n "$1" '[.[] | select(.name == $n)][0].id // empty') || exit 1
  [[ -n "$id" ]] || fail "No home named '$1' (run 03-user-register-home.sh first)"
  echo "$id"
}

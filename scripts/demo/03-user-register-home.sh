#!/usr/bin/env bash
# User: register a home.   Usage: ./03-user-register-home.sh "<name>" [timezone, default UTC]
source "$(dirname "$0")/_common.sh"
[[ $# -ge 1 ]] || fail "Usage: $0 \"<name>\" [timezone]"

bold "User registers home '$1'"
api POST /homes/register "$(jq -n --arg n "$1" --arg tz "${2:-UTC}" '{name: $n, timezone: $tz}')" | jq .

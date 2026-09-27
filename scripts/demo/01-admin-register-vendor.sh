#!/usr/bin/env bash
# Admin: register a vendor.   Usage: ./01-admin-register-vendor.sh <SAMSUNG|AMAZON|CISCO> "<name>"
source "$(dirname "$0")/_common.sh"
[[ $# -eq 2 ]] || fail "Usage: $0 <SAMSUNG|AMAZON|CISCO> \"<name>\""

bold "Admin registers vendor $1"
api POST /vendors/register "$(jq -n --arg c "$1" --arg n "$2" '{code: $c, name: $n}')" | jq .

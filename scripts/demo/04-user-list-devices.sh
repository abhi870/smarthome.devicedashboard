#!/usr/bin/env bash
# User: show the supported devices they can register.
# Usage: ./04-user-list-devices.sh [VENDOR_CODE] [DEVICE_TYPE]      e.g.  ./04-user-list-devices.sh AMAZON AC
source "$(dirname "$0")/_common.sh"

query=()
[[ -n "${1:-}" ]] && query+=("vendorId=$(vendor_id_by_code "$1")")
[[ -n "${2:-}" ]] && query+=("deviceType=$2")
path="/devices"
[[ ${#query[@]} -gt 0 ]] && path+="?$(IFS='&'; echo "${query[*]}")"

bold "Supported devices${1:+ from $1}${2:+ of type $2}"
api GET "$path" | jq -r '
  (["VENDOR", "TYPE", "MODEL", "NAME", "METRICS"] | @tsv),
  (.[] | [.vendorCode, .deviceType, .model, .name, ([.mappings[].metric] | join(","))] | @tsv)' | column -t -s $'\t'
info "Register one with: ./05-user-register-home-device.sh \"<home>\" <MODEL> <external-device-id> \"<name>\""

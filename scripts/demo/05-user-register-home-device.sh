#!/usr/bin/env bash
# User: register one of their appliances (a supported model) in their home.
# Usage: ./05-user-register-home-device.sh "<home name>" <MODEL> <external-device-id> "<name>" [polling seconds, default 300]
# Example: ./05-user-register-home-device.sh "My home" AZ-AC12 amz-ac-01 "Bedroom AC" 60
source "$(dirname "$0")/_common.sh"
[[ $# -ge 4 ]] || fail "Usage: $0 \"<home name>\" <MODEL> <external-device-id> \"<name>\" [polling seconds]"

home_id=$(home_id_by_name "$1")
device_id=$(device_id_by_model "$2")
bold "User registers '$4' ($2, vendor id $3) in home '$1'"
api POST /home-devices/register "$(jq -n --arg h "$home_id" --arg d "$device_id" --arg e "$3" --arg n "$4" \
  --argjson p "${5:-300}" '{homeId: $h, deviceId: $d, externalDeviceId: $e, name: $n, pollingIntervalSeconds: $p}')" | jq .
info "All devices in '$1':"
api GET "/home-devices?homeId=$home_id" | jq -r '
  (["NAME", "VENDOR", "MODEL", "EXTERNAL ID", "EVERY (s)"] | @tsv),
  (.[] | [.name, .vendorCode, .model, .externalDeviceId, .pollingIntervalSeconds] | @tsv)' | column -t -s $'\t'

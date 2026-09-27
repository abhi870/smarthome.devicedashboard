#!/usr/bin/env bash
# Admin: add a supported device model to the catalogue, with its metric mappings.
#
# Usage: ./02-admin-register-device.sh <VENDOR_CODE> <DEVICE_TYPE> <MODEL> "<name>" <mapping>...
#   mapping     = externalMetric:METRIC[:CONVERSION]      (conversion defaults to NONE)
#   DEVICE_TYPE = TV | REFRIGERATOR | AC | OVEN | WASHER | DRYER
#   METRIC      = TEMPERATURE | POWER | ENERGY | HUMIDITY | RUNTIME | DOOR_OPEN_COUNT | SWITCH
#   CONVERSION  = NONE | F_TO_C | K_TO_C | KW_TO_W | WH_TO_KWH | SECONDS_TO_MINUTES | HOURS_TO_MINUTES
#
# Example:
#   ./02-admin-register-device.sh AMAZON AC AZ-AC12 "Amazon Smart AC 12K" \
#       powerState:SWITCH powerConsumption:POWER:KW_TO_W energyUsage:ENERGY temperature:TEMPERATURE
source "$(dirname "$0")/_common.sh"
[[ $# -ge 5 ]] || fail "Usage: $0 <VENDOR_CODE> <DEVICE_TYPE> <MODEL> \"<name>\" externalMetric:METRIC[:CONVERSION]..."

vendor_code="$1" device_type="$2" model="$3" name="$4"
shift 4

mappings="[]"
for spec in "$@"; do
  IFS=: read -r external metric conversion <<<"$spec"
  [[ -n "$external" && -n "$metric" ]] || fail "Bad mapping '$spec' (expected externalMetric:METRIC[:CONVERSION])"
  mappings=$(jq --arg e "$external" --arg m "$metric" --arg c "${conversion:-NONE}" \
    '. + [{externalMetric: $e, metric: $m, conversion: $c}]' <<<"$mappings")
done

vendor_id=$(vendor_id_by_code "$vendor_code")
bold "Admin adds $vendor_code $model ($device_type) to the catalogue"
api POST /devices/register "$(jq -n --arg v "$vendor_id" --arg t "$device_type" --arg mo "$model" --arg n "$name" \
  --argjson maps "$mappings" '{vendorId: $v, deviceType: $t, model: $mo, name: $n, mappings: $maps}')" | jq .

#!/usr/bin/env bash
# Walks through the whole flow with the Amazon AC from the mock vendors service, pausing between steps.
# Run on an empty database (docker compose down -v) with the app on :8080.
set -euo pipefail
cd "$(dirname "$0")"
step() { echo; read -r -p "▶ $1  [enter]"; }

step "1. Admin registers vendor Amazon"
./01-admin-register-vendor.sh AMAZON "Amazon"

step "2. Admin adds the Amazon Smart AC to the catalogue, with metric mappings"
./02-admin-register-device.sh AMAZON AC AZ-AC12 "Amazon Smart AC 12K" \
  powerState:SWITCH powerConsumption:POWER:KW_TO_W energyUsage:ENERGY temperature:TEMPERATURE

step "3. User registers their home"
./03-user-register-home.sh "My home" Asia/Kolkata

step "4. User browses devices available to register"
./04-user-list-devices.sh

step "5. User registers their AC (vendor device id amz-ac-01)"
./05-user-register-home-device.sh "My home" AZ-AC12 amz-ac-01 "Bedroom AC" 60

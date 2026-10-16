#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
command -v docker >/dev/null || { echo 'Docker is required' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'Python 3 is required to read base-image.json' >&2; exit 1; }
export SEMGREP_BASE_IMAGE
SEMGREP_BASE_IMAGE="$(python3 -c 'import json,re; b=json.load(open("base-image.json")); r=b["reference"]; assert re.fullmatch(r"semgrep/semgrep@sha256:[0-9a-f]{64}",r); print(r)')"
export SCAN_IMAGE="${SCAN_IMAGE:-foundry/semgrep-scan:local}"
docker version --format '{{.Server.Version}}'
export SCAN_PLATFORM="${SCAN_PLATFORM:-linux/$(docker version --format '{{.Server.Arch}}')}"
case "$SCAN_PLATFORM" in linux/amd64|linux/arm64) ;; *) echo "Unsupported platform: $SCAN_PLATFORM" >&2; exit 1 ;; esac
docker compose version
docker compose config --quiet
docker pull --platform "$SCAN_PLATFORM" "$SEMGREP_BASE_IMAGE"
docker compose build --pull=false semgrep-scan
echo "Built $SCAN_IMAGE"
if [[ "${1:-}" == '--start' ]]; then
  docker compose up -d --no-build --pull never
fi

#!/usr/bin/env bash
# convenience: jump into the docker dev shell
set -euo pipefail
IMAGE="${1:-overlay-cam:android}"
docker run --rm -it \
  -v "$PWD":/work \
  -v "$HOME/.gradle":/.gradle \
  -w /work \
  "$IMAGE" bash
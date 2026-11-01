#!/usr/bin/env bash
# 로컬 kind 클러스터 지움
set -euo pipefail
kind delete cluster --name ggeolmuse

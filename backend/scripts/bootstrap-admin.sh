#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
read -r -p '최초 관리자 아이디 [admin]: ' AEGIS_BOOTSTRAP_NAME
AEGIS_BOOTSTRAP_NAME="${AEGIS_BOOTSTRAP_NAME:-admin}"
read -r -p '관리자 이메일 (새 계정만 필요): ' AEGIS_BOOTSTRAP_EMAIL
read -r -s -p '비밀번호 (기존 계정이면 기존 비밀번호): ' AEGIS_BOOTSTRAP_PASSWORD
printf '\n'
export AEGIS_BOOTSTRAP_NAME AEGIS_BOOTSTRAP_EMAIL AEGIS_BOOTSTRAP_PASSWORD
trap 'unset AEGIS_BOOTSTRAP_PASSWORD' EXIT
bash gradlew bootJar --no-daemon --console=plain
java -jar build/libs/aegisvault-0.0.1-SNAPSHOT.jar --spring.profiles.active=bootstrap-admin "$@"

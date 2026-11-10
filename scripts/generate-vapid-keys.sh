#!/usr/bin/env bash
# 웹 푸시(VAPID) P-256 키쌍을 openssl로 만들어 서버 .env에 추가한다.
# 이미 WEB_PUSH_PUBLIC_KEY·WEB_PUSH_PRIVATE_KEY가 있으면 건드리지 않고 안내만 한다.
# 개인키는 화면에 출력하지 않는다.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".env"

if [ -f "$ENV_FILE" ] && grep -q '^WEB_PUSH_PUBLIC_KEY=.\+' "$ENV_FILE" 2>/dev/null; then
	echo "이미 .env에 WEB_PUSH_PUBLIC_KEY가 있어요. 덮어쓰지 않습니다."
	echo "새 키로 바꾸려면 .env에서 WEB_PUSH_PUBLIC_KEY·WEB_PUSH_PRIVATE_KEY 줄을 먼저 지우고 다시 실행하세요."
	exit 0
fi

TMP_KEY=$(mktemp)
trap 'rm -f "$TMP_KEY"' EXIT

openssl ecparam -genkey -name prime256v1 -noout -out "$TMP_KEY"

b64url() {
	xxd -r -p | base64 | tr '+/' '-_' | tr -d '=\n'
}

PRIV_HEX=$(openssl ec -in "$TMP_KEY" -noout -text 2>/dev/null \
	| sed -n '/priv:/,/pub:/p' | sed '1d;$d' | tr -d ' :\n')
PUB_HEX=$(openssl ec -in "$TMP_KEY" -noout -text 2>/dev/null \
	| sed -n '/pub:/,/ASN1 OID/p' | sed '1d;$d' | tr -d ' :\n')

# priv는 32바이트(64 hex) 스칼라다. DER 인코딩 때문에 앞에 00 한 바이트가 더 붙어 나올 수 있어 뒤 64자리만 쓴다.
PRIV_HEX=${PRIV_HEX: -64}

PUB_KEY=$(echo -n "$PUB_HEX" | b64url)
PRIV_KEY=$(echo -n "$PRIV_HEX" | b64url)

{
	echo "WEB_PUSH_PUBLIC_KEY=${PUB_KEY}"
	echo "WEB_PUSH_PRIVATE_KEY=${PRIV_KEY}"
} >> "$ENV_FILE"

echo "VAPID 키를 만들어 .env에 추가했어요."
echo "공개키(프론트 설정 등에 써도 되는 값): ${PUB_KEY}"
echo "개인키는 .env에만 저장했고 화면에는 보여주지 않습니다."
echo "WEB_PUSH_SUBJECT(mailto:주소)는 .env에 직접 적어 주세요."

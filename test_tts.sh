#!/usr/bin/env bash
# test_tts.sh — Validate Google Cloud TTS API key before using it in the app.
#
# Usage:
#   chmod +x test_tts.sh
#   ./test_tts.sh <API_KEY>
#
# Get a key at: https://console.cloud.google.com/apis/credentials
# Valid Google API keys start with "AIzaSy"

set -euo pipefail

KEY="${1:-}"

if [[ -z "$KEY" ]]; then
  echo "Usage: ./test_tts.sh <API_KEY>"
  echo ""
  echo "  Get your API key from: https://console.cloud.google.com/apis/credentials"
  echo "  A valid Google API key starts with 'AIzaSy'"
  exit 1
fi

# Warn about wrong key format
if [[ ! "$KEY" =~ ^AIza ]]; then
  echo "⚠️  WARNING: This doesn't look like a Google Cloud API key."
  echo "   Google API keys start with 'AIzaSy...'"
  echo "   Your key starts with: '${KEY:0:8}...'"
  echo ""
  echo "   If you have an OAuth token or service account JSON — those are different."
  echo "   Go to: https://console.cloud.google.com/apis/credentials"
  echo "   Click 'Create Credentials' → 'API key'"
  echo ""
fi

echo "Testing Google Cloud TTS API (v1beta1)..."
echo ""

TMPFILE=$(mktemp)
HTTP_CODE=$(curl -s -o "$TMPFILE" -w "%{http_code}" -X POST \
  "https://texttospeech.googleapis.com/v1beta1/text:synthesize?key=${KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "input": { "ssml": "<speak>\u05e9\u05dc\u05d5\u05dd</speak>" },
    "voice": { "languageCode": "he-IL", "name": "he-IL-Wavenet-A" },
    "audioConfig": { "audioEncoding": "MP3", "speakingRate": 0.9 }
  }')

BODY=$(cat "$TMPFILE")
rm -f "$TMPFILE"

if [[ "$HTTP_CODE" == "200" ]]; then
  AUDIO_INFO=$(echo "$BODY" | python3 -c "
import sys, json
d = json.load(sys.stdin)
c = d.get('audioContent', '')
kb = len(c) * 3 // 4 // 1024
print(f'{len(c)} chars base64 (~{kb} KB MP3)')
" 2>/dev/null || echo "received")

  echo "✅  Google Cloud TTS: OK"
  echo "    Voice      : he-IL-Wavenet-A (Hebrew WaveNet female)"
  echo "    Audio      : $AUDIO_INFO"
  echo ""
  echo "Your API key is working correctly. Save it in the app:"
  echo "  Settings → Buddy AI Configuration → Google Cloud TTS API Key"
else
  echo "❌  Google Cloud TTS: FAILED (HTTP $HTTP_CODE)"
  echo ""

  ERROR_MSG=$(echo "$BODY" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    e = d.get('error', {})
    status  = e.get('status', '?')
    code    = e.get('code', '?')
    message = e.get('message', '?')
    print(f'Status : {status}')
    print(f'Code   : {code}')
    print(f'Message: {message}')
except Exception:
    print(sys.stdin.read()[:500])
" 2>/dev/null || echo "$BODY")

  echo "$ERROR_MSG"
  echo ""

  if echo "$ERROR_MSG" | grep -qi "API_KEY_INVALID\|API key not valid"; then
    echo "→ The key is invalid or has not been activated yet."
    echo "  Wait a few minutes after creating a key and try again."
  elif echo "$ERROR_MSG" | grep -qi "PERMISSION_DENIED\|disabled\|not enabled"; then
    echo "→ Cloud Text-to-Speech API is not enabled for this project."
    echo "  Enable it at: https://console.cloud.google.com/apis/library/texttospeech.googleapis.com"
  elif echo "$ERROR_MSG" | grep -qi "CREDENTIALS_MISSING\|restricted"; then
    echo "→ The key has Android app restrictions. The app will add X-Android-Package/X-Android-Cert"
    echo "  headers automatically — this test script doesn't send them."
    echo "  Try removing app restrictions in Cloud Console to confirm the key itself is valid."
  fi
fi

Release a new version of this app. Arguments: optional version string e.g. "1.2.0". If not provided, auto-increment the patch version.

Run this entire block as **one Bash call**. Do not split it up. If the build fails, show the last 30 lines of output and stop — do not tag or publish.

```bash
set -euo pipefail
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd "$(git rev-parse --show-toplevel)"

# ── Read config ───────────────────────────────────────────────────────────────
APP_CONFIG=$(find app/src -name "AppConfig.kt" | head -1)
APP_NAME=$(grep 'APP_NAME' "$APP_CONFIG" | sed 's/.*"\([^"]*\)".*/\1/' | head -1)
RELEASES_OWNER=$(grep 'GITHUB_RELEASES_REPO_OWNER' "$APP_CONFIG" | sed 's/.*"\([^"]*\)".*/\1/' | head -1)
RELEASES_REPO=$(grep 'GITHUB_RELEASES_REPO_NAME' "$APP_CONFIG" | sed 's/.*"\([^"]*\)".*/\1/' | head -1)
OLD_CODE=$(grep 'versionCode[[:space:]]*=' app/build.gradle.kts | sed 's/[^0-9]*\([0-9]*\).*/\1/' | head -1)
OLD_NAME=$(grep 'versionName[[:space:]]*=' app/build.gradle.kts | sed 's/.*"\([^"]*\)".*/\1/' | head -1)

[[ -n "$APP_NAME" ]]       || { echo "❌ Could not read APP_NAME";       exit 1; }
[[ -n "$RELEASES_OWNER" ]] || { echo "❌ Could not read RELEASES_OWNER"; exit 1; }
[[ -n "$RELEASES_REPO" ]]  || { echo "❌ Could not read RELEASES_REPO";  exit 1; }
[[ -n "$OLD_CODE" ]]       || { echo "❌ Could not read versionCode";    exit 1; }
[[ -n "$OLD_NAME" ]]       || { echo "❌ Could not read versionName";    exit 1; }

echo "Current: versionCode=$OLD_CODE  versionName=$OLD_NAME  app=$APP_NAME"

# ── Determine new version ────────────────────────────────────────────────────
NEW_NAME="${ARGUMENTS:-}"
if [[ -z "$NEW_NAME" ]]; then
  PATCH=$(echo "$OLD_NAME" | cut -d. -f3)
  NEW_NAME="$(echo "$OLD_NAME" | cut -d. -f1-2).$((PATCH + 1))"
fi
NEW_CODE=$((OLD_CODE + 1))

echo "Releasing: versionCode=$NEW_CODE  versionName=$NEW_NAME"

# ── Pre-flight ────────────────────────────────────────────────────────────────
[[ -f keystore.properties ]] || { echo "❌ keystore.properties missing"; exit 1; }
[[ "$(git branch --show-current)" == "main" ]] || { echo "❌ not on main"; exit 1; }

# ── Commit dirty tracked files ───────────────────────────────────────────────
if git status --porcelain | grep -q '^.M\|^M'; then
  git add -u
  git commit -m "chore: pre-release changes for v${NEW_NAME}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
fi

# ── Bump version ─────────────────────────────────────────────────────────────
sed -i '' "s/versionCode = ${OLD_CODE}/versionCode = ${NEW_CODE}/" app/build.gradle.kts
sed -i '' "s/versionName = \"${OLD_NAME}\"/versionName = \"${NEW_NAME}\"/" app/build.gradle.kts

ACTUAL_CODE=$(grep 'versionCode[[:space:]]*=' app/build.gradle.kts | sed 's/[^0-9]*\([0-9]*\).*/\1/' | head -1)
ACTUAL_NAME=$(grep 'versionName[[:space:]]*=' app/build.gradle.kts | sed 's/.*"\([^"]*\)".*/\1/' | head -1)
[[ "$ACTUAL_CODE" == "$NEW_CODE" ]] || { echo "❌ versionCode bump failed (got $ACTUAL_CODE)"; exit 1; }
[[ "$ACTUAL_NAME" == "$NEW_NAME" ]] || { echo "❌ versionName bump failed (got $ACTUAL_NAME)"; exit 1; }

git add app/build.gradle.kts
git commit -m "chore: release v${NEW_NAME}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"

# ── Push commits ─────────────────────────────────────────────────────────────
git push origin main &
git remote | grep -q '^github$' && git push github main &
wait

# ── Build ─────────────────────────────────────────────────────────────────────
# JVM/daemon args are intentionally omitted here — gradle.properties already
# configures them. Passing them on the CLI creates a daemon mismatch and forces
# a cold daemon start every time.
CPUS=$(sysctl -n hw.logicalcpu 2>/dev/null || nproc 2>/dev/null || echo 8)

./gradlew assembleRelease \
  --parallel \
  --build-cache \
  --configuration-cache \
  --max-workers="$CPUS" \
  -Dorg.gradle.workers.max="$CPUS" \
  -x lintVitalRelease

# ── Tag & publish ─────────────────────────────────────────────────────────────
APK_SLUG="${APP_NAME// /-}"
APK_FILE="${APK_SLUG}-v${NEW_NAME}.apk"

cp app/build/outputs/apk/release/app-release.apk "$APK_FILE"
APK_SIZE=$(du -sh "$APK_FILE" | cut -f1)

git tag "v${NEW_NAME}"
git push origin "v${NEW_NAME}" &
git remote | grep -q '^github$' && git push github "v${NEW_NAME}" &
wait

gh release create "v${NEW_NAME}" \
  --repo "${RELEASES_OWNER}/${RELEASES_REPO}" \
  --title "${APP_NAME} v${NEW_NAME}" \
  --notes "## What's new
Release v${NEW_NAME}" \
  "$APK_FILE"

rm "$APK_FILE"

echo ""
echo "✅ Released ${APP_NAME} v${NEW_NAME}"
echo "   versionCode : ${NEW_CODE}"
echo "   APK size    : ${APK_SIZE}"
echo "   GitHub      : https://github.com/${RELEASES_OWNER}/${RELEASES_REPO}/releases/tag/v${NEW_NAME}"
```

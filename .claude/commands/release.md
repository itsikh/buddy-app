Release a new version of this app. Arguments: optional version string e.g. "1.2.0". If not provided, auto-increment the patch version.

## Phase 1 — Prepare & build (single bash block)

Run this entire block as **one Bash call**. It reads config, validates, commits, bumps version, pushes, and starts the build. Do not split it up.

```bash
set -euo pipefail
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd "$(git rev-parse --show-toplevel)"

# ── Read config ───────────────────────────────────────────────────────────────
APP_CONFIG=$(find app/src -name "AppConfig.kt" | head -1)
APP_NAME=$(grep 'APP_NAME' "$APP_CONFIG" | grep -oP '"[^"]+"' | tr -d '"')
RELEASES_OWNER=$(grep 'GITHUB_RELEASES_REPO_OWNER' "$APP_CONFIG" | grep -oP '"[^"]+"' | tr -d '"')
RELEASES_REPO=$(grep 'GITHUB_RELEASES_REPO_NAME' "$APP_CONFIG" | grep -oP '"[^"]+"' | tr -d '"')
OLD_CODE=$(grep 'versionCode\s*=' app/build.gradle.kts | grep -oP '\d+')
OLD_NAME=$(grep 'versionName\s*=' app/build.gradle.kts | grep -oP '"[^"]+"' | tr -d '"')

# ── Determine new version ────────────────────────────────────────────────────
NEW_NAME="${ARGUMENTS:-}"
if [[ -z "$NEW_NAME" ]]; then
  PATCH=$(echo "$OLD_NAME" | cut -d. -f3)
  NEW_NAME="$(echo "$OLD_NAME" | cut -d. -f1-2).$((PATCH + 1))"
fi
NEW_CODE=$((OLD_CODE + 1))

# ── Pre-flight ────────────────────────────────────────────────────────────────
[[ -f keystore.properties ]] || { echo "❌ keystore.properties missing"; exit 1; }
[[ "$(git branch --show-current)" == "main" ]] || { echo "❌ not on main"; exit 1; }

# ── Commit any dirty tracked files ───────────────────────────────────────────
if git status --porcelain | grep -q '^.M\|^M'; then
  git add -u
  git commit -m "chore: pre-release changes for v${NEW_NAME}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
fi

# ── Bump version ─────────────────────────────────────────────────────────────
sed -i '' "s/versionCode = ${OLD_CODE}/versionCode = ${NEW_CODE}/" app/build.gradle.kts
sed -i '' "s/versionName = \"${OLD_NAME}\"/versionName = \"${NEW_NAME}\"/" app/build.gradle.kts
git add app/build.gradle.kts
git commit -m "chore: release v${NEW_NAME}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"

# ── Push ──────────────────────────────────────────────────────────────────────
git push origin main &
git remote | grep -q '^github$' && git push github main &
wait

# ── Build ─────────────────────────────────────────────────────────────────────
CPUS=$(sysctl -n hw.logicalcpu 2>/dev/null || nproc 2>/dev/null || echo 8)
MEM_GB=$(python3 -c "
import subprocess
m = int(subprocess.run(['sysctl','hw.memsize'],capture_output=True,text=True).stdout.split()[1])
print(max(6, m // 1024 // 1024 // 1024 - 2))
" 2>/dev/null || echo 8)

./gradlew assembleRelease \
  --parallel \
  --build-cache \
  --configuration-cache \
  --max-workers="$CPUS" \
  -Dorg.gradle.workers.max="$CPUS" \
  -Dorg.gradle.jvmargs="-Xmx${MEM_GB}g -XX:+UseG1GC -XX:MaxMetaspaceSize=1g -XX:+AlwaysPreTouch -XX:+HeapDumpOnOutOfMemoryError" \
  -Dkotlin.incremental=true \
  -Dkotlin.daemon.jvm.options="-Xmx${MEM_GB}g -XX:+UseG1GC" \
  -Pkotlin.compiler.execution.strategy=in-process

# ── Export for Phase 2 ────────────────────────────────────────────────────────
echo "RELEASE_APP_NAME=$APP_NAME"
echo "RELEASE_NEW_NAME=$NEW_NAME"
echo "RELEASE_NEW_CODE=$NEW_CODE"
echo "RELEASE_OWNER=$RELEASES_OWNER"
echo "RELEASE_REPO=$RELEASES_REPO"
```

If the build fails, stop and show the last 30 lines of output. Do NOT continue to Phase 2.

Parse the `RELEASE_*` values printed at the end — you need them for Phase 2.

## Phase 2 — Tag, upload & publish (single bash block)

Substitute the `RELEASE_*` values parsed from Phase 1 output, then run as **one Bash call**.

```bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

APP_NAME="<RELEASE_APP_NAME>"
NEW_NAME="<RELEASE_NEW_NAME>"
NEW_CODE="<RELEASE_NEW_CODE>"
RELEASES_OWNER="<RELEASE_OWNER>"
RELEASES_REPO="<RELEASE_REPO>"

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

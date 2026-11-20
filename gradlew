#!/usr/bin/env sh
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -Xmx1536m -jar gradle/wrapper/gradle-wrapper.jar "$@"
elif command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "Gradle wrapper jar not found and system gradle is unavailable." >&2
  exit 1
fi

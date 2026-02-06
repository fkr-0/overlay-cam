#!/usr/bin/env bash
set -euo pipefail

# if the wrapper is missing, bootstrap it using the system gradle in the image
if [ ! -f "./gradlew" ]; then
	echo "Gradle wrapper missing. Initializing with Gradle ${GRADLE_VERSION:-8.9}..."
	/opt/gradle/bin/gradle wrapper --gradle-version 8.9
fi

chmod +x ./gradlew
./gradlew --no-daemon "$@"

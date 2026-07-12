#!/usr/bin/env bash
set -euo pipefail

# Make the Makefile's mounted host Gradle cache effective inside Docker.
# Without this, Gradle defaults to /root/.gradle and may redownload the wrapper
# on every container run, making offline/local validation flaky.
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/.gradle}"
mkdir -p "$GRADLE_USER_HOME"

# if the wrapper is missing, bootstrap it using the system gradle in the image
if [ ! -f "./gradlew" ]; then
	echo "Gradle wrapper missing. Initializing with Gradle ${GRADLE_VERSION:-8.9}..."
	/opt/gradle/bin/gradle wrapper --gradle-version 8.9
fi

chmod +x ./gradlew
./gradlew --no-daemon "$@"

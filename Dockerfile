FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/usr/lib/jvm/java-17-openjdk-amd64/bin:/opt/gradle/bin:$PATH

# 1) base deps + JDK
RUN apt-get update && apt-get install -y --no-install-recommends \
      curl unzip zip git ca-certificates openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

# 2) install Android cmdline-tools so sdkmanager is at .../cmdline-tools/latest/bin
RUN set -eux; \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools; \
    cd /tmp; \
    curl -sSLo cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip; \
    unzip -q cmdtools.zip; rm -f cmdtools.zip; \
    mv cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest; \
    ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager --version

# 3) accept licenses and install SDK bits
RUN yes | sdkmanager --licenses
RUN sdkmanager \
      "platform-tools" \
      "platforms;android-34" \
      "build-tools;34.0.0" \
      "cmake;3.22.1" \
      "ndk;26.1.10909125" || true

# 4) install Gradle 8.9 and verify by absolute path
ENV GRADLE_VERSION=8.9
RUN set -eux; \
    cd /tmp; \
    curl -sSLO "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"; \
    mkdir -p /opt/gradle-tmp; \
    unzip -q "gradle-${GRADLE_VERSION}-bin.zip" -d /opt/gradle-tmp; \
    rm "gradle-${GRADLE_VERSION}-bin.zip"; \
    mv "/opt/gradle-tmp/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}"; \
    rmdir /opt/gradle-tmp; \
    ls -la "${GRADLE_HOME}/bin"; \
    "${GRADLE_HOME}/bin/gradle" --version

WORKDIR /work
CMD ["/bin/bash"]

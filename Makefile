PROJECT ?= overlay-cam
IMAGE   ?= overlay-cam:android
WORKDIR ?= /work

.PHONY: docker docker-nocache build clean test install shell up ci

docker:
	docker build -t $(IMAGE) .

# if Docker cached the broken layer before, use this once:
docker-nocache:
	docker build --no-cache -t $(IMAGE) .
# == end/Makefile ==

# Gradle build inside Docker; mount project and gradle caches for speed
build:
	docker run --rm \
	  -v $$PWD:$(WORKDIR) \
	  -v $$HOME/.gradle:/.gradle \
	  -w $(WORKDIR) \
	  $(IMAGE) \
	  bash -lc "bash ./scripts/gradle-in-docker.sh assembleDebug"

clean:
	docker run --rm \
	  -v $$PWD:$(WORKDIR) \
	  -v $$HOME/.gradle:/.gradle \
	  -w $(WORKDIR) \
	  $(IMAGE) \
	  bash -lc "bash ./scripts/gradle-in-docker.sh clean"

test:
	docker run --rm \
	  -v $$PWD:$(WORKDIR) \
	  -v $$HOME/.gradle:/.gradle \
	  -w $(WORKDIR) \
	  $(IMAGE) \
	  bash -lc "bash ./scripts/gradle-in-docker.sh testDebugUnitTest"

shell:
	docker run --rm -it \
	  -v $$PWD:$(WORKDIR) \
	  -v $$HOME/.gradle:/.gradle \
	  -w $(WORKDIR) \
	  $(IMAGE) bash

# Install APK via host adb
install:
	adb install -r app/build/outputs/apk/debug/app-debug.apk

# Compose: convenient cache + one-shot build
up:
	docker compose up --build --abort-on-container-exit

# Simulate CI locally
ci: docker build test

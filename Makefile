GRADLE := ./gradlew
GRADLE_FLAGS := --no-daemon

.PHONY: test build run android-debug

test:
	$(GRADLE) $(GRADLE_FLAGS) :shared:jvmTest :sharedUi:jvmTest :desktopApp:test

build:
	$(GRADLE) $(GRADLE_FLAGS) :desktopApp:jar

run:
	$(GRADLE) $(GRADLE_FLAGS) :desktopApp:run

android-debug:
	$(GRADLE) $(GRADLE_FLAGS) :app:assembleDevDebug

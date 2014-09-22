all:
	./gradlew assembleRelease

clean:
	./gradlew clean

install_debug:
	./gradlew installDebug

debug_apk:
	./gradlew assembleDebug

test:
	./gradlew testUnittest

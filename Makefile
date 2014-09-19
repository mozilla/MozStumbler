all:
	./gradlew assembleRelease

install_debug:
	./gradlew installDebug

debug_apk:
	./gradlew assembleDebug

test:
	./gradlew testUnittest

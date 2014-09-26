all:
	./gradlew assembleRelease

clean:
	./gradlew clean

install_debug:
	./gradlew installDebug

debug:
	./gradlew assembleDebug

test:
	./gradlew testUnittest

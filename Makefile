test: updateJars
	./gradlew testUnittest

debug: updateJars
	./gradlew assembleDebug

release: updateJars
	./gradlew assembleRelease

updateJars:
	./gradlew -q updateJars

clean:
	./gradlew clean

install_debug:
	./gradlew installDebug

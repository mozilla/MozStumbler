test: updateJars
	./gradlew testUnittest

debug: updateJars
	./gradlew assembleDebug

release: updateJars
	# private.properties has our special sauce for Mozilla builds
	cp private.properties gradle.properties
	./gradlew assembleRelease

updateJars:
	./gradlew -q updateJars

clean:
	./gradlew clean

install_debug:
	./gradlew installDebug

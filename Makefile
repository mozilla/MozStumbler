test: updateJars
	./gradlew testUnittest

release_check:
	. ./release_check.sh

debug: updateJars
	./gradlew assembleDebug

release: release_check updateJars
	./gradlew assembleRelease
	. ./rename_release.sh

updateJars:
	./gradlew -q updateJars

clean:
	./gradlew clean

install_debug:
	./gradlew installDebug

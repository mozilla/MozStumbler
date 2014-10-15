test: unittest
	./gradlew testUnittest --info

release_check:
	. ./release_check.sh

unittest: updateJars
	./gradlew assembleUnittest

debug: updateJars
	./gradlew assembleDebug

release: release_check updateJars
	./gradlew assembleRelease
	sh rename_release.sh

updateJars:
	./gradlew -q updateJars

clean:
	rm -rf android/libs/*.jar
	./gradlew clean

install_debug:
	./gradlew installDebug

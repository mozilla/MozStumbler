test: unittest
	./gradlew testUnittest --info

release_check:
	./release_check.py

unittest:
	./gradlew assembleUnittest

debug:
	./gradlew assembleDebug

release: release_check
	./gradlew assembleRelease
	sh rename_release.sh

clean:
	rm -rf android/libs/*.jar
	./gradlew clean

install_debug:
	./gradlew installDebug

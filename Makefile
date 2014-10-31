test: unittest
	./gradlew testUnittest --info

release_check:
	./release_check.py

unittest:
	./gradlew assembleUnittest

debug:
	./gradlew assembleDebug

github: release_check
	./gradlew assembleGithub
	sh rename_release.sh github

playstore: release_check
	./gradlew assemblePlaystore
	sh rename_release.sh playstore

clean:
	rm -rf android/libs/*.jar
	./gradlew clean

install_debug:
	./gradlew installDebug

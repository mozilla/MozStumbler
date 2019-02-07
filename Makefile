test: libtest unittest
	./gradlew copyTestResources
	./gradlew testGithubUnittest --info

libtest:
	cd libraries/stumbler; ./gradlew test

unittest:
	./gradlew assembleGithubUnittest

debug:
	./gradlew assembleGithubDebug

github:
	./release_check.py github
	./gradlew assembleGithubRelease
	sh rename_release.sh github-release

playstore:
	./release_check.py playstore
	./gradlew assemblePlaystoreRelease
	sh rename_release.sh playstore-release

fdroid:
	./gradlew assembleFdroidRelease
	sh rename_release.sh fdroid-release

clean:
	rm -rf outputs
	rm -rf libraries/stumbler/build
	./gradlew clean

install_debug:
	./gradlew installGithubDebug

test: libtest unittest
	./gradlew copyTestResources
	./gradlew testGithubLbDevUnittest --info

libtest:
	cd libraries/stumbler; ./gradlew test

unittest:
	./gradlew assembleGithubLbdevUnittest

debug:
	./gradlew assembleGithubLbdevDebug

github:
	./release_check.py github
	./gradlew assembleGithubLbprodRelease
	sh rename_release.sh github-release

playstore:
	./release_check.py playstore
	./gradlew assemblePlaystoreLbprodRelease
	sh rename_release.sh playstore-release

fdroid:
	./gradlew assembleFdroidLbprodRelease
	sh rename_release.sh fdroid-release

clean:
	rm -rf outputs
	rm -rf libraries/stumbler/build
	./gradlew clean

install_debug:
	./gradlew installGithubLbdevDebug

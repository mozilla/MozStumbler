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
	sh rename_release.sh github-lbprod-release

playstore:
	./release_check.py playstore
	./gradlew assemblePlaystoreLbprodRelease
	sh rename_release.sh playstore-lbprod-release

fdroid:
	./gradlew assembleFdroidLbprodRelease
	sh rename_release.sh fdroid-lbprod-release

clean:
	rm -rf outputs
	rm -rf libraries/stumbler/build
	./gradlew clean

install_debug:
	./gradlew installGithubLbdevDebug

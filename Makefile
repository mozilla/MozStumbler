test: unittest
	./gradlew testGithubUnittest --info


unittest:
	./gradlew assembleUnittest

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
	rm -rf outputs/*
	./gradlew clean

install_debug:
	./gradlew installGithubDebug

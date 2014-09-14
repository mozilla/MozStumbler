all:
	./gradlew build

test:
	./gradlew connectedAndroidTest --info

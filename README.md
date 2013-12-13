MozStumbler
[![Build Status](https://travis-ci.org/dougt/MozStumbler.png)](https://travis-ci.org/dougt/MozStumbler.png)

# Building #

```
./gradlew build
./gradlew installRelease
```

# Signing #

In order to sign the APK, you will want to create a 'gradle.properties' file.  The content should look like the following except it should contain your key signing
information:

```
StoreFile=<path to file>
StorePassword=<password>
KeyAlias=<key alias>
KeyPassword=<password>
MozAPIKey=test
TileServerURL=<OSM Tile Server>
```
For the OSM (Open Street Maps) Tile Server, you have several options:

* *[MapBox](https://www.mapbox.com/)* (easy, secure) is a hosted OSM solution, allowing users to easily create beautiful maps and featuring full SSL. To use MapBox:

  1. Visit [mapbox.com](https://www.mapbox.com/) and sign up
  2. From the [MapBox Dashboad](https://www.mapbox.com/dashboard/) click the big blue "New Project" button
  3. Customize your map as you please. The only requirement is that you allow public API access. To do this click on the gear in the white box at the top, select the "Advanced" option at the bottom, and uncheck the "Hide project from public API" box. Be sure to hit the save button after doing this.
  4. Obtain the API key for your map (visible from the dashboard under Projects and Data, or in the URL of the editor)
  5. Set `TileServerURL` in the `gradle.propeties` file to `https://a.tiles.mapbox.com/v3/<API key>/`. Do not miss the tailing slash, it will break things if you do.i

  *Note that, for historical reasons, you can simply specify the API key in the `gradle.properties` file and not the full URL, using the `MapAPIKey` key*

* *Another hosted solution* (difficulty varies, as does security). There are many OSM tile servers available. [This is a nice list of some](http://switch2osm.org/providers/).

* Run your own Tile Server (advanced, as secure as you want)
  You can, of course, run your own tileserver. [Switch2OSM](http://switch2osm.org/serving-tiles/) has several excellent guides on the subject and is a good place to get started

To generate a signing key, search the internet for details.  This command is probably what you want:

```
keytool -genkey -v -keystore my-release-key.keystore -alias mozstumbler -keyalg RSA -keysize 2048 -validity 10000
```

To verify the apk has been signed, you can run this command:

```
jarsigner -verify -verbose -certs build/apk/MozStumbler*
```

# Releasing #

This release process ought to be automated.

1. `MOZSTUMBLER_VERSION=x.y.z`
2. `git checkout -b v$MOZSTUMBLER_VERSION`
2. `echo $MOZSTUMBLER_VERSION > VERSION`
2. Increment `android:versionCode` and `android:versionName` in the AndroidManifest.xml.in file.
3. `git commit -m "MozStumbler v$MOZSTUMBLER_VERSION" AndroidManifest.xml.in VERSION`
4. Push the new version branch to GitHub and file a new pull requests so Travis can start building it.
5. `./gradlew build`
6. `mv build/apk/MozStumbler-release.apk build/apk/MozStumbler-$MOZSTUMBLER_VERSION.apk`
7. Browse to https://github.com/dougt/MozStumbler/releases.
8. "Draft a new release" with the release title "MozStumbler v$MOZSTUMBLER_VERSION" and tag version "v$MOZSTUMBLER_VERSION".
9. Add some release notes and give @credit to contributors!
10. Drag and drop the new MozStumbler-v$MOZSTUMBLER_VERSION.apk to the "Attach binaries for this release by dropping them here." box.
11. Check "This is a pre-release" because perpetual beta.
12. **Save draft**. Do *not* "Publish release" yet because master does not have the new VERSION file!
13. Merge the new version pull request. **NB:** *There is a race condition between steps 13 and 14!* It is mostly harmless, but be quick about it.
14. *Now* go back to the draft release and **Publish release**.
15. Email a release announcment to the dev-geolocation mailing list with release notes giving @credit to contributors and a link to the release's page https://github.com/dougt/MozStumbler/releases/tag/v$MOZSTUMBLER_VERSION.
16. Good work!  Pat yourself on your back.

MozStumbler
[![Build Status](https://travis-ci.org/mozilla/MozStumbler.png)](https://travis-ci.org/mozilla/MozStumbler.png)

# Building a debug version from command line #

```
make
```

# Building a debug version from Android Studio #

[![Add new run configuration](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/add_new_config.png)](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/add_new_config.png)

Setup the Android Application to use two gradle aware make targets.
Make sure you run updateJars first, then assembleDebug.

[![Setup new run configuration](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/setup_android_config.png)](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/setup_android_config.png)

# Signing #

In order to sign the APK, you will want to create a 'gradle.properties' file.  The content should look like the following except it should contain your key signing
information:

```
StoreFile=<path to file>
StorePassword=<password>
KeyAlias=<key alias>
KeyPassword=<password>
MozAPIKey=<mozilla location service api key>
MapAPIKey=<osm tile server key>
TileServerURL=<osm tile server>  # optional
```
For the OSM (OpenStreetMap) Tile Server, you have several options:

* *[MapBox](https://www.mapbox.com/)* (easy, secure) is a hosted OSM
  solution, allowing users to easily create beautiful maps and
  featuring full SSL. To use MapBox:

  1. Visit [mapbox.com](https://www.mapbox.com/) and sign up
  2. From the [MapBox Dashboad](https://www.mapbox.com/dashboard/)
     click the big blue "Create Project" button
  3. Customize your map as you please. The only requirement is that
     you allow public API access. To do this click on the gear in the
     white box at the top, select the "Advanced" option at the bottom, and
     uncheck the "Hide project from public API" box. Be sure to hit the
     save button after doing this.
  4. Obtain the API key for your map (visible from the dashboard under
     Projects and Data, or in the URL of the editor)
  5. Set `TileServerURL` in the `gradle.propeties` file to
     `http://api.tiles.mapbox.com/v3/<API key>/`. Do not miss the
     tailing slash, it will break things if you do.i

*Note that, for historical reasons, you can simply specify the API
key in the `gradle.properties` file and not the full URL, using the
`MapAPIKey` key*

* *Another hosted solution* (difficulty varies, as does security).
  There are many OSM tile servers available. [This is a nice list of
  some](http://switch2osm.org/providers/).

* Run your own Tile Server (advanced, as secure as you want)
  You can, of course, run your own tileserver.
  [Switch2OSM](http://switch2osm.org/serving-tiles/) has several
  excellent guides on the subject and is a good place to get started.

To generate a signing key, search the internet for details.  This
command is probably what you want:

```
keytool -genkey -v -keystore my-release-key.keystore -alias mozstumbler -keyalg RSA -keysize 2048 -validity 10000
```

To verify the apk has been signed, you can run this command:

```
jarsigner -verify -verbose -certs android/build/outputs/apk/android-release.apk
```

Note that if signing failed, the gradle build system should not generate an android-release.apk file.
You will probably find only the `android-release-unsigned.apk` file.

# Releasing #

The release process is largely automated.

1. Bump the version numbers at the top of android/build.gradle
2. Make sure you've got a private.properties file at the top level of
   the project.
3. You should have your keystore file at the top level of the project.
4. Run `make release`
5. You should end up with a signed apk MozStumbler-v<whatever_the_version_is>.apk in the rootProject/outputs directory.
6. Browse to https://github.com/mozilla/MozStumbler/releases.
7. "Draft a new release" with the release title "MozStumbler v$MOZSTUMBLER_VERSION" and tag version "v$MOZSTUMBLER_VERSION".
8. Add some release notes and give @credit to contributors!
9. Drag and drop the new MozStumbler-v$MOZSTUMBLER_VERSION.apk to the "Attach binaries for this release by dropping them here." box.
10. Check "This is a pre-release" because perpetual beta.
11. Save the changes.
12. Tag this release in github with the release version number so that
    we can reproduce the build (eg: v0.9.0.0).
13. Push the changes and tag to GitHub and file a PR to merge to
    master.
14. Merge the new version pull request. When the new VERSION file is
    merged in, clients will be able to download the new release.
15. Email a release announcment to the dev-geolocation mailing list with release notes giving @credit to contributors and a link to the release's page https://github.com/mozilla/MozStumbler/releases/tag/v$MOZSTUMBLER_VERSION.
14. Good work!  Pat yourself on your back.

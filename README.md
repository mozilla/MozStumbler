MozStumbler
[![Build Status](https://travis-ci.org/dougt/MozStumbler.png)](https://travis-ci.org/dougt/MozStumbler.png)

```
gradlew build
gradlew installRelease
```

Signing

In order to sign the APK, you will want to create a 'gradle.properties' file.  The content should look like the following except it should contain your key signing
information:

```
StoreFile=<path to file>
StorePassword=<password>
KeyAlias=<key alias>
KeyPassword=<password>.
```

To generate a key, search the internet for details.  This command is probably what you want:

```
keytool -genkey -v -keystore my-release-key.keystore -alias mozstumbler -keyalg RSA -keysize 2048 -validity 10000
```

To verify the apk has been signed, you can run this command:

```
jarsigner -verify -verbose -certs build/apk/MozStumbler*
```


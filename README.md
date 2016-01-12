Please refer to the [wiki](https://github.com/mozilla/MozStumbler/wiki) for detailed documentation.

MozStumbler
[![Build Status](https://travis-ci.org/mozilla/MozStumbler.png)](https://travis-ci.org/mozilla/MozStumbler.png)

# Building a debug version from command line #

The build system is smart enough to automatically download and install
all the parts of the Android SDK for you.  If you cannot build, you
can either try to fix your Android dev enviroment to fit the
android/build.gradle requirements - or you can simply remove
ANDROID_HOME, and all traces of your Android SDK from your PATH.

```
make
```

# Building a debug version from Android Studio #

[![Edit run configuration](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/edit_configuration.png)](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/edit_configuration.png)


[![Add new run configuration](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/add_new_config.png)](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/add_new_config.png)

Setup the Android Application to use two gradle aware make targets.
You must set 'Installation Options' to "Deploy Nothing".

The tricky part is to set the build tasks.  You will need two tasks of
type 'Gradle-Aware Make'.  Android Studio will autocomplete the names
below when you start typing them in.

1.  :android:assembleGithubDebug
2.  :android:installGithubDebug

[![Setup new run configuration](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/setup_android_config.png)](https://raw.githubusercontent.com/mozilla/MozStumbler/dev/docs/screencaps/setup_android_config.png)

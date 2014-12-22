Testing
=======

Testing for the Mozilla Stumbler is handled through several tools that
work together.

We use roboelectric to run fast unit tests within a regular JVM.  This
is not 100% problem free, but it allows your tests to run fast.

We use a Service Locator pattern
(http://martinfowler.com/articles/injection.html#UsingAServiceLocator)
to inject dependencies at runtime.

We are currently investigating the use of robotium to instrument the
full client application to get more realistic tests that run on a real
Android VM where good tests cannot be written using robolectric.


Robolectric for dummies
-----------------------

Testing in Android is just hard.  The standard test tools run slowly
so we use robolectric to run tests quickly on a regular JVM. This lets
your tests run in seconds instead of minutes. 

Robolectric provides `Shadow` classes for all Android classes in the
`android.*` namespace.  A `Shadow` can be though of as a
mock implementation of the equivalent Android class. 

Robolectric tests run within JUnit4.  You need to provide extra
annotations to tell Robolectric to run with a custom test runner, a
target SDK and any custom shadow you may have.

Limitations
-----------

Roboelectric 2.4 only supports up to Android API level 18 
(Jelly Bean MR2).

Shadow classes explicitly implements stubs to methods that will be
shadowed.  In some cases, you will need to fork and extend the
roboelectric project and install your own robolectric JAR file.  In
that case, you will need to also update your android/build.gradle file
to use `mavenLocal()` as a depdendency repository after doing a `mvn
install` for your custom robolectric build.

In many cases, you will want to customize the behavior of an Android
class.  Typically, the solution to this is that you will want to
subclass a `Shadow` implementation, and then annotate your test so
that your custom shadow is used.

Examples
--------

A good basic example can be found in LogActivityTest at:

https://github.com/mozilla/MozStumbler/blob/features/1294-motion-sensor-tests/android/src/test/java/org/mozilla/mozstumbler/client/subactivities/LogActivityTest.java#L31

You will find an example there of loading a custom shadow to provide
specialized behavior as well as the standard annotations you will need
for your tests to run.


After your tests are run, you will get an HTML report.  If you run the
testGithubUnittest target, your HTML output will appear in :

`android/build/test-report/github/unittest/index.html`

Android Studio integration
--------------------------

TODO: Get instructions from here :

http://blog.blundell-apps.com/how-to-run-robolectric-junit-tests-in-android-studio/


Simulation Mode
---------------

There is a new developer option to toggle simulation mode on.  You
will need to toggle your stumbler off and on once after enabling the
simulation.  You should see the cell tower count stay at 1, wifi count
at 2, and the position should incrementally move to the north east.

You will need to first enable mock locations in your Android developer
options or else this feature won't work.

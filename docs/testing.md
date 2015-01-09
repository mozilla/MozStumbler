Testing
=======

Testing for the Mozilla Stumbler is handled through several tools that
work together.

We use roboelectric to run fast unit tests within a regular JVM.  This
is not 100% problem free, but it allows your tests to run fast.

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

To run your tests, from the command line, the easiest thing to do is
to use the test.sh shell script provided in the root directory of
Mozilla Stumbler. Gradle expects that you to pass in a pattern
matching the fully qualified classname post-fixed with the method you
wish to test.  So something like this ::

`./gradlew testGithubUnittest --tests --tests org.mozilla.mozstumbler.client.UpdaterTest.testUpdater`

The shell script uses a wildcard so that you can simply use:

`./test.sh UpdaterTest.testUpdater`


Injecting Dependencies 
-----------------------

Mozilla Stumbler provides two mechanisms to inject dependencies into
your code. Service Location is used primarily to decompose the
application into logical components which are looked up using the
ServiceLocator class.  The Service Locator solves two problems,
acquiring a reference to an implementation of a known interface, as
well as being able to inject new implementations of that interface
that dependant classes can start using.

We currently inject a clock service, as well as a simplified HTTP
client.  By doing this, we can change the system time while running
under test by supplying an alternate test implementation of the
ISystemClock.  The IHttpClient interface has a similar role.  The
default implementation can be replaced as better APIs appear, but we
can also replace the implementation when we run under test and stub
out all network calls.

The second mechanism to inject dependencies, but this only works while
running under test is to use [mockito](https://github.com/mockito/mockito).

Mockito provides two mechanisms to mock out classes, the mock()
function and the spy() function.  mock() can take a
class, and return a usable implementation.  This also conveniently
works when passing in interface classes.  mock() will provide a pure
mock - a class that does a no-op for every method call.

Calling spy() on an instantiated object will allow you to override the
methods of a live object with new methods.  You can optionally also
call the original method in your spy object.  In most cases, this is
probably what you want when writing tests as you can easily replace
methods to do a no-op and focus on the part of your code that you are
interested in.


Some tips
---------

It's helpful to read the code in the MapFragmentTest.  That test case
needs to instantiate a Fragment, have it run through the create
portion of the activity life cycle.

In general, you probably want to minimize the length of methods which
overload base android classes by pushing out most logic into a
separate function so that you can use robolectric to run a fragment or
activity through the creation phase of activity life cycle, but also
stub out the portions of the create function that aren't important to
the portion of code you are interested in testing.

Service Lookup
--------------

To use the service lookup code, you want to look at the
`org.mozilla.mozstumbler.svclocator` package.  Using the service
locator is relatively straight forward, we use a Service Locator
pattern
(http://martinfowler.com/articles/injection.html#UsingAServiceLocator)
to inject dependencies at runtime.

The pattern that we use to add services is to use
ServiceLocator::putService(<interface class>, implementation).

When you need to acquire a reference to a service, you call 
ServiceLocator::getService(<interface class>).

Android Studio integration
--------------------------

We provide a Groovy live-plugin extension that can be used to run a
single test method from within Android Studio 1.0 - the latest version
at the time time this documentation was written.  To use it, you will
need to install the 'LivePlugin' plugin to Android Studio and install
the plugin from `android_studio/plugin.groovy`.

The video at http://youtu.be/y8lUv7YJexI demonstrates how to properly
add this plugin.

Usage is very straight forward.  Just focus your cursor inside the
method you wish to test, and hit Ctrl-Shift-t.  As long as the method
you're in starts with the `test`, this should "just work".  If it
doesn't - please file a bug at https://github.com/mozilla/MozStumbler/issues

Simulation Mode
---------------

There is a new developer option to toggle simulation mode on.  You
will need to toggle your stumbler off and on once after enabling the
simulation.  You should see the cell tower count stay at 1, wifi count
at 2, and the position should incrementally move to the north east.

You will need to first enable mock locations in your Android developer
options or else this feature won't work.

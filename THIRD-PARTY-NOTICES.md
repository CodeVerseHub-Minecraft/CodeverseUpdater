# Third party notices

CodeverseUpdater bundles nothing. It has no runtime dependencies at all: it
speaks to GitHub with the JDK's own `java.net.http.HttpClient` and parses JSON
with a small reader written for this library. A plugin adopting it inherits no
transitive libraries and has nothing to relocate or attribute.

The test suite uses JUnit and the JDK's built in `com.sun.net.httpserver` to
stand up a local server, neither of which is present in the published jar.

## About

This project is maintained by the CodeVerseHub-Minecraft Subteam, which works
alongside the wider CodeVerseHub community but is a separate team. CodeVerseHub
is not responsible for these projects.

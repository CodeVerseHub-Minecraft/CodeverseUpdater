# CodeverseUpdater

A small library that keeps a plugin up to date from its GitHub releases. It
checks for a newer release, verifies the download against a published checksum,
and stages the new jar for the next restart. It has no runtime dependencies.

## What it does, and what it deliberately does not

It downloads a newer release, checks the jar against the SHA-256 the release
published, and writes the verified jar into the server's update folder, which
the platform swaps in on the next restart. That is the whole mechanism.

It does not replace a running jar in place, because the platform holds it open
and doing so is unreliable. It does not apply an update without a restart unless
a plugin explicitly opts in. And it never stages a jar it could not verify: a
release with no published checksum is treated as not updatable rather than
downloaded on trust.

Staging rather than auto applying is the default on purpose. Auto updating a
plugin that guards accounts or permissions turns a compromised release token
into code that runs on the next restart with nobody in the loop. A plugin whose
compromise is less total can turn auto apply on; the guarded ones should not.

## Adding it to a plugin

Depend on it through JitPack:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.CodeVerseHub-Minecraft.CodeverseUpdater:updater:0.1.2")
}
```

Shade it as you would any library. It brings nothing else with it.

Then, in your plugin's startup, build an updater and check on your own
scheduler. The check makes a network request, so it must run off the main
thread; every platform has an async scheduler for this.

```java
Updater updater = new Updater(UpdaterConfig
        .forRepository("CodeVerseHub-Minecraft", "CodeverseAuth")
        .currentVersion(pluginVersion)
        .updateFolder(updateFolder)
        .targetJarName("CodeverseAuth-" + pluginVersion + ".jar")
        .build());

updater.checkAsync(asyncExecutor, result -> {
    switch (result) {
        case UpdateResult.UpToDate ignored ->
                logger.info("CodeverseAuth is up to date.");
        case UpdateResult.UpdateAvailable available ->
                logger.info("CodeverseAuth {} is available. Run your apply command to stage it.",
                        available.release().tag());
        case UpdateResult.Staged staged ->
                logger.info("CodeverseAuth {} staged. Restart to apply.", staged.release().tag());
        case UpdateResult.Failed failed ->
                logger.warn("Update check failed: {}", failed.reason());
    }
});
```

`updateFolder` is the platform's update directory. On Paper it is
`plugins/update`. On Velocity there is no built in update folder, so a plugin
picks a directory and swaps the jar in on shutdown; the library only stages,
the swap is the plugin's to arrange.

`targetJarName` must match the installed jar's filename, so the staged jar
replaces it rather than sitting beside it. When unset it defaults to the
repository name with a `.jar` suffix, which is only right if that matches.

## Applying a staged update on command

When auto apply is off, a check reports `UpdateAvailable` and stages nothing.
An admin command can stage it on request by passing the release back to
`stage`, which verifies before writing exactly as the automatic path does:

```java
// held from the UpdateAvailable result
UpdateResult staged = updater.stage(release);
```

After staging, the update applies on the next restart. A command that wants to
apply immediately schedules a restart itself; the library does not restart the
server for you.

## Configuration reference

| Setting | Default | Meaning |
|---|---|---|
| `currentVersion` | required | the running version, usually the plugin's own |
| `updateFolder` | required | where verified jars are staged |
| `targetJarName` | repo name + `.jar` | the filename the staged jar takes |
| `includePreReleases` | false | whether pre-releases are considered |
| `autoApply` | false | whether a found update is staged without asking |
| `checkInterval` | 6 hours, floored at 15 minutes | how often a background check runs |

The interval floor exists because unauthenticated GitHub allows sixty requests
an hour per address. Several plugins on one host share that budget, so a short
interval buys nothing a human could act on faster.

## Publishing a release the updater can read

The updater needs two things on a release: a jar asset, and a SHA-256 for it.
The checksum can be either a sidecar asset named after the jar with a `.sha256`
suffix, or a line in the release body pairing the hash with the jar's name.
Both are common; either works. A release with a jar but no checksum is skipped,
by design.

## The library updating itself

A library shaded into a plugin cannot replace itself, since there is no
separate jar on disk. So `SelfUpdate` does not hot swap. It knows its own
version and can tell you when a newer updater has been published, so you learn
that the plugins bundling it are due a rebuild. It reports; the rebuild is
yours to do.

## Building

```bash
./gradlew clean build
```

JDK 25 to build, though the library targets 21 so consumers are not forced onto
25. The test suite stands up a local HTTP server and runs fully offline.

## License

MIT. See [LICENSE](LICENSE).

## About

This project is maintained by the CodeVerseHub-Minecraft Subteam, which works
alongside the wider CodeVerseHub community but is a separate team. CodeVerseHub
is not responsible for these projects.
EOF
echo "README written"; wc -l README.md
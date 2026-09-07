# Publishing to Maven Central (local first)

Coordinates: `de.sfxr:mindi`. The release version is declared in `gradle.properties`.
Check Central and choose an unused version before uploading. The group ID `de.sfxr` represents ownership
of `sfxr.de`. Confirm the namespace and version before uploading: Central releases
are immutable and a published version cannot be overwritten.

Publishing uses the Central Portal through `com.vanniktech.maven.publish`, not
the retired OSSRH/Nexus endpoints. For automated tag releases, see
[GitHub release setup](github-releases.md).

## One-time account and signing setup

1. Sign in at <https://central.sonatype.com/>. Register/verify namespace `de.sfxr`
   using the DNS TXT record supplied by the Portal for `sfxr.de`. If you previously
   used OSSRH, check whether the namespace has been migrated to your account.
2. Generate a **Portal user token** in your account settings. Its username and
   password are separate from your website login.
3. Use an existing release-signing key, or generate one interactively:

   ```sh
   gpg --full-generate-key
   gpg --list-secret-keys --keyid-format LONG
   ```

   Choose RSA (3072 or 4096 bits), a suitable expiration date, and a strong
   passphrase. Back up the private key and revocation certificate securely.
4. Publish only the **public** key, using its full fingerprint:

   ```sh
   gpg --keyserver hkps://keyserver.ubuntu.com --send-keys YOUR_FINGERPRINT
   ```

   Allow time for availability. See Sonatype's [signing requirements](https://central.sonatype.org/publish/requirements/gpg/).

Account login, DNS changes, token creation, and choosing/unlocking your private
key are owner-only steps. Never paste tokens or private keys into chat or commit
them to this repository.

## Load credentials in your terminal

These commands use Bash and keep secret values out of shell history. Do not
run with shell tracing (`set -x`) enabled.

```bash
read -r -p 'Central token username: ' ORG_GRADLE_PROJECT_mavenCentralUsername
read -r -s -p 'Central token password: ' ORG_GRADLE_PROJECT_mavenCentralPassword; echo
read -r -p 'Signing key fingerprint: ' SIGNING_FINGERPRINT
read -r -s -p 'Signing passphrase: ' ORG_GRADLE_PROJECT_signingInMemoryKeyPassword; echo
export ORG_GRADLE_PROJECT_mavenCentralUsername ORG_GRADLE_PROJECT_mavenCentralPassword
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys "$SIGNING_FINGERPRINT")"
```

The last command captures the private key instead of printing it. GPG may prompt
to unlock it. Alternatively, configure credentials in your user-level Gradle
properties file (outside the repository, permissions `0600`), following the
[plugin documentation](https://vanniktech.github.io/gradle-maven-publish-plugin/central/#secrets).
The old `signingKey` / `signingPassword` environment variable names are no longer used.

## Test and stage locally

Use Linux x86-64 and JDK 21 to run the Gradle wrapper. Gradle provisions JDK 11
for JVM compilation and downloads the Kotlin/Native and Node toolchains.

```sh
./gradlew jvmTest jsNodeTest linuxX64Test
./gradlew clean publishAllPublicationsToLocalRepository --no-configuration-cache
```

The second command signs and writes all six publications to `build/local-maven/`:

- `mindi` (multiplatform metadata)
- `mindi-jvm`
- `mindi-js`
- `mindi-linuxx64`
- `mindi-linuxarm64`
- `mindi-mingwx64`

Inspect POMs, Gradle module metadata, binaries/KLIBs, sources, Dokka javadoc JARs,
and `.asc` signatures. This does **not** contact Central to upload anything and
needs only the signing key, not the Portal token. Signing without a configured
key intentionally fails rather than silently producing an unsigned release.
Do not run the generic `publish` task for a local check: it includes remote repositories.

Linux ARM64 and Windows artifacts can be compiled here. Windows runtime tests
run on Windows in CI. Linux ARM64 is cross-compiled only: Kotlin/Native currently
does not support Linux ARM64 compiler hosts. Browser tests are currently disabled.

## Upload, validate, then release

Once local checks pass and the coordinates are confirmed:

```sh
./gradlew publishToMavenCentral --no-configuration-cache
```

This uploads a deployment without automatically releasing it. Open
<https://central.sonatype.com/publishing/deployments>, wait for **VALIDATED**, and
inspect any validation errors. Click **Publish** only when ready. A deployment
that is not published can be discarded and corrected.

For subsequent releases, once this process is proven, the explicit one-command
upload-and-release path is:

```sh
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

Update `version` in `gradle.properties` before a release. If using `FORCED_VERSION`
to override it locally, use the same value for validation and upload. Never reuse an
already-published version. Avoid snapshots for the first release: they use a
separate repository and do not verify the release workflow.

After publishing, wait for Central propagation (typically 10–30 minutes), then
verify from a separate consumer using **only** `mavenCentral()`:

```kotlin
repositories { mavenCentral() }
dependencies { implementation("de.sfxr:mindi:0.2.0") }
```

A plain Maven/JVM consumer uses `de.sfxr:mindi-jvm:0.2.0`. Confirm dependency
resolution and a small usage test before declaring the release successful.

Finally clear credentials from the terminal:

```sh
unset ORG_GRADLE_PROJECT_mavenCentralUsername ORG_GRADLE_PROJECT_mavenCentralPassword
unset ORG_GRADLE_PROJECT_signingInMemoryKey ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
unset SIGNING_FINGERPRINT
```

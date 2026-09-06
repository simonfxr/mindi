# GitHub release setup

## One-time preparation

In <https://github.com/simonfxr/mindi/settings/environments>, create an environment
named **`maven-central`**. Add these **environment secrets**:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user-token username, not your login |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user-token password |
| `SIGNING_KEY` | Entire ASCII-armored private key file, including BEGIN/END lines and newlines |
| `SIGNING_PASSWORD` | Private key passphrase |

The existing verified namespace `de.sfxr` and published public signing key are
sufficient; no new namespace or key is needed. Keep an offline encrypted backup
of the private key and its revocation certificate.

If GitHub CLI is authenticated locally, upload the key without printing it:

```sh
gh secret set SIGNING_KEY --repo simonfxr/mindi --env maven-central < ~/code-signing-private-key.asc
```

Use the GitHub UI or interactive `gh secret set` prompts for the other values.
Do not pass secret values as command-line arguments or commit them to files.
The obsolete `SONATYPE_*` / `GPG_*` secret names are not used by the new workflow.

In the environment's protection settings:

- Add yourself as a required reviewer if supported by your repository/plan.
  If you are the only maintainer, leave **Prevent self-review** disabled so you
  can approve your own tag release.
- Restrict deployment to selected **tags** matching `v*`; do not select only
  protected branches, which would block tag deployments.
- Restrict who can create/update release tags via a repository ruleset where
  available. Do not move or delete published release tags.

Under **Settings → Actions → General**, enable GitHub Actions and allow the
pinned actions used in `.github/workflows/`. Publishing uses only `contents: read`;
no PAT, GitHub Packages token, or `packages: write` permission is needed.
The separate documentation workflow needs `contents: write` for its existing
`gh-pages` branch deployment. For that website, set **Settings → Pages** to
"Deploy from a branch", branch `gh-pages`, folder `/` once the branch exists.
Pages is optional and unrelated to Maven publishing.

## Release 0.2.0

1. Review and commit the release changes. Confirm `version=0.2.0` in
   `gradle.properties`.
2. Push the branch and wait for the Build and Run Tests workflows to pass.
3. Create an annotated tag if it does not already exist, then push **that tag**:

   ```sh
   git push origin main
   # Wait for branch CI; omit tag creation if already prepared locally.
   git tag -a v0.2.0 -m "Release 0.2.0"
   git push origin v0.2.0
   ```

4. The **Publish to Maven Central** workflow runs tests on the tagged commit:
   JVM, Node.js, Linux x64, and Windows x64. Linux ARM64 is cross-compiled, not
   runtime-tested. No signing secrets are given to test jobs or PR workflows.
5. Approve the `maven-central` environment when prompted. The release job checks
   that the tag matches the declared version, builds and signs all six
   publications, then runs `publishAndReleaseToMavenCentral`. It waits for
   Central's **PUBLISHED** state. **Approval authorizes an immutable public
   release**, not just a staged upload.
6. Verify the release with a separate consumer using only `mavenCentral()`.
   Central propagation may still take time after the publishing step completes.

Creating a GitHub Release is optional: **pushing the tag is the trigger**. Do not
also publish this version manually from your workstation.

## Failures and retries

- Tests fail: fix them on the branch before releasing. Never overwrite an already
  published version. Local checks cannot substitute for the Windows runner.
- Missing secrets: populate the environment secrets, then rerun the failed job.
- Validation failure: inspect the Central Portal deployment's errors. Discard an
  unpublished failed deployment before retrying the corrected release.
- Upload timeout or ambiguous failure: **check Central before rerunning**. It may
  already have published the version. Do not blindly re-upload immutable artifacts.
- If the workflow succeeded but downloads briefly return 404, allow time for
  Central propagation rather than rerunning publishing.

Gradle configuration caching is disabled for publishing, the publish job does
not persist Gradle caches, and secrets are scoped to its single publish step.
No private key files or release-job build artifacts are uploaded to GitHub.

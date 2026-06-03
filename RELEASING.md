# Releasing Tsunagi to Maven Central

Everything in the repository is already configured for publishing (the `release`
profile in `pom.xml` and the `.github/workflows/release.yml` workflow). The steps
below are the **one-time account setup** and the **per-release** actions that only
the project owner can perform, because they need personal credentials.

## One-time setup

1. **Central Portal account** — sign up at <https://central.sonatype.com>.
   Register the namespace `io.github.diegoalegil`; because it maps to the GitHub
   account `github.com/diegoalegil`, it can be verified automatically.

2. **Publishing token** — in the Portal, *Account → Generate User Token*. Keep the
   generated username and password; they become `CENTRAL_USERNAME` /
   `CENTRAL_PASSWORD`.

3. **GPG key** — create a signing key and publish its public half:
   ```bash
   gpg --gen-key
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --armor --export-secret-keys <KEY_ID> > private-key.asc   # for CI
   ```

4. **GitHub repository secrets** — *Settings → Secrets and variables → Actions*:
   - `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` — the token from step 2
   - `GPG_PRIVATE_KEY` — the contents of `private-key.asc`
   - `GPG_PASSPHRASE` — the key's passphrase

## Releasing via GitHub Actions (recommended)

1. Make sure `pom.xml` has the version you want to release (e.g. `1.0.0`, no
   `-SNAPSHOT`) and that `CHANGELOG.md` has an entry for it.
2. Tag and push:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. The `Release` workflow builds, signs and uploads the artifacts to the Central
   Portal as a **staged** deployment.
4. Review and **Publish** the deployment from the Portal UI. It appears on Maven
   Central a short while later.

## Releasing manually from your machine

Add the token to `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>central</id>
    <username>CENTRAL_TOKEN_USERNAME</username>
    <password>CENTRAL_TOKEN_PASSWORD</password>
  </server>
</servers>
```

Then, with the GPG key available locally:

```bash
mvn -Prelease deploy
```

and publish the staged deployment from the Portal.

## After a release

1. Bump the version to the next development snapshot, e.g. `0.2.0-SNAPSHOT`.
2. Add a new `## [Unreleased]` section at the top of `CHANGELOG.md`.
3. Commit.

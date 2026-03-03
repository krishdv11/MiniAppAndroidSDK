# Publishing MiniApp SDK

This project is configured to publish `miniapp-android-sdk` as a Maven artifact.

## Coordinates

- `groupId`: `com.digitral`
- `artifactId`: `miniapp-android-sdk`
- `version`: `1.0.0`

## 2) Build and publish locally (quick verification)

```bash
./gradlew :miniappsdk:assembleRelease
./gradlew :miniappsdk:publishReleasePublicationToLocalRepoRepository
```

Local test repository output:

- `build/repo`

## 3) Publish to remote Maven repository

Set repository credentials in `gradle.properties` or environment variables:

- `PUBLISH_REPOSITORY_URL`
- `PUBLISH_REPOSITORY_USERNAME`
- `PUBLISH_REPOSITORY_PASSWORD`

Then publish:

```bash
./gradlew :miniappsdk:publishReleasePublicationToRemoteRepository
```

## 4) Signing (required for Maven Central)

Configure:

- `SIGNING_KEY` (ASCII-armored private key)
- `SIGNING_PASSWORD`

With signing configured, publications are signed automatically.

## 5) GitHub recommendation

Keep source code on GitHub and publish artifacts to Maven (Maven Central or GitHub Packages).  
This gives:

- source transparency
- issue tracking and release notes
- easy `implementation("group:artifact:version")` consumption for all projects

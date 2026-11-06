# Releasing

The source lives at https://github.com/doriru89/ride-compare. No release has been published yet.

1. After device validation, create a Release with tag `v0.14`. While the new features remain unverified on a device, mark it as a pre-release.
2. Attach `RideCompare-0.14.apk` as a release asset. The automatically generated GitHub source ZIP is not an installable app.
3. Link the release page for installation:

```text
https://github.com/doriru89/ride-compare/releases/latest
```

For a pre-release, use its individual release-page URL; GitHub's `latest` route does not designate pre-releases as the latest stable release. Do not publish placeholder URLs as if they already exist.

Keep signing keys private and stable across releases. The new public source archive deliberately excludes the private development keystore; older private working archives may include it and are not intended for upload. Publishing an APK does not publish its private signing key.

## F-Droid

The official F-Droid repository is not just an APK file host. Its submission process requires a public source repository, a free-software license (MIT qualifies), a build review and compliance with its inclusion policy. No submission has been made.

Official references:

- https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/

## Prepared APK

`RideCompare-0.14.apk` SHA-256:

```text
5b2d5497e0bd725e6eabada69ef491ab7d720a6c3ffdb29396d4deff7a9f04a5
```

# Ride Compare

Choose a destination once. Open Uber and Lyft with the same pickup and destination, side by side on a compatible Android foldable.

A small personal utility built around one recurring frustration: comparing rides should not require opening two apps and entering the same address twice. This project explores how a focused, locally installed tool can remove that friction with existing Android capabilities.

**Version:** 0.14 · **Status:** personal prototype / early testing

## Try it

Build the APK using the instructions below. A prepared 0.14 APK exists, but a GitHub Release has not yet been published. There is no hosted service or account to create.

1. In Google Maps, open a place and share it to **Ride Compare**.
2. The shared place fills the destination. Pickup defaults to your current location each time you share a new place.
3. Edit either address if needed. If only an address name is available, select the correct search result with ✓.
4. Tap **比较价格**. The whole Uber / split-screen / Lyft panel is one button.
5. Review the prices and route in the two ride apps. Booking remains in their apps.

You can also type a destination, tap **Paste from clipboard**, or open Google Maps from its icon beside **常用地址**.

### Saved places

Tap **常用地址 → 添加地址**, search for an address, select its result, give it a name such as `work` or `home`, then save it. Tap a saved place to use it as the destination. The × beside it deletes it after confirmation. Names must be unique; up to 50 places are stored locally. No personal places are built into the source.

## Requirements and limitations

- Android 12L / API 32 or newer. Both Uber and Lyft must be installed to compare them.
- Designed for Samsung foldables. Earlier builds were tested by the author on a Fold; the 0.14 saved-place dialog still needs device validation.
- Android and the ride apps control split-screen behavior. Start from Ride Compare in full screen; some devices require manual split screen. Phones without suitable multi-window support can open each ride app separately from the menu.
- This app passes coordinates to the ride apps. It does **not** fetch, scrape, calculate or guarantee fares, and does not book rides.
- Google Maps short links require internet access. Some links return intermittent HTTP 404s. A short-link 404 gets one retry; persistent failures remain visible and are not treated as resolved. The underlying intermittent failure has not been reproduced reliably.
- Link formats and system address search can vary. Camera/viewport coordinates are never silently treated as a selected place. Airport terminal and pickup-zone choice may still require adjustment inside the ride apps.
- This is not an Uber, Lyft or Google product. Their names and app icons identify the installed apps; the icons are loaded from Android, not bundled in this repository.

## Build and inspect

No Gradle project or third-party runtime SDK is required. The build uses the Android SDK command-line tools and Java sources directly.

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Bash, `zip` and `keytool`.

```bash
export ANDROID_SDK_ROOT=/absolute/path/to/android-sdk
bash build.sh
bash test.sh
```

Output: `build/ride-compare.apk`.

If only a Java runtime is installed, the scripts also accept `ECJ_JAR` pointing to Eclipse ECJ 3.38.0. The compiler itself is not included. A normal JDK installation is the simplest path.

A fresh build generates a local development signing key under `.private/`, which is ignored by Git. It cannot update an APK signed by someone else's key. To publish updates under your own stable signing identity, keep that key private and pass:

```bash
export RIDE_KEYSTORE=/absolute/path/to/your-signing-key.p12
export RIDE_KEY_ALIAS=your-alias
export RIDE_STORE_PASSWORD=your-store-password
export RIDE_KEY_PASSWORD=your-key-password
bash build.sh
```

Never commit those environment values or the keystore. The public source package contains no signing keys, compiled APKs, compiler JARs or saved-place data.

| File | Responsibility |
| --- | --- |
| `MainActivity.java` | Route editing, location lifecycle, share input and launching the ride apps |
| `MapsShare.java` | Bounded HTTPS share-link resolution and destination parsing |
| `PickupState.java` | GPS/manual pickup state and stale callback rejection |
| `SavedPlaces.java` | Validated address-book data and its versioned local encoding |
| `SavedPlacesStore.java` | App-private persistence |
| `SavedPlacesDialog.java` | Search, confirm, name, choose and delete saved addresses |
| `CompareButton.java`, `RouteGlyph.java`, `NeoDrawable.java` | Custom black/purple interface drawing |

See [privacy](docs/PRIVACY.md), [validation](docs/VALIDATION.md), and [release steps](docs/RELEASING.md).

## License

MIT, see [LICENSE](LICENSE). Uber, Lyft and Google Maps are trademarks of their owners; this project is not affiliated with them. The repository is not yet prepared for the official F-Droid repository, which also requires a build review and a submission.

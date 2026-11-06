# Privacy and data flow

Ride Compare has no backend, advertising SDK, analytics SDK, account system or telemetry endpoint.

- **Location:** GPS is requested for the current pickup. A manually selected pickup can be used without location permission. The app passes selected pickup and destination coordinates to Uber or Lyft when you launch them.
- **Address search:** Typed queries are sent to the device's Android geocoding provider. That provider's network behavior and privacy terms apply.
- **Maps shares:** The app reads a link or text that you explicitly share or paste. HTTPS requests follow only the supported Google hosts. Network requests can reveal the chosen link and your IP address to that service.
- **Clipboard:** Read only when you tap the Paste control. There is no background clipboard monitoring.
- **Saved places:** Names, formatted addresses and coordinates are stored in app-private preferences on this device. Saved places are not synced. You can delete individual places in the dialog; clearing app storage or uninstalling removes them. Cloud backup is disabled in the manifest.
- **Route editing:** Android can retain temporary route state across activity recreation. The app does not write a ride history or save fare/payment/account data.
- **Installed apps:** Android package visibility is limited to the three integrated apps. Their icons are obtained locally through PackageManager.

The application requests Internet and foreground coarse/fine location permissions. It does not request accessibility service, contacts, background location, notification access or external-storage access.

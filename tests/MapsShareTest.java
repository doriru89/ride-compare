import com.orz.ridecompare.MapsShare;

public class MapsShareTest {
  private static int tests;

  private static void check(boolean ok, String name) {
    tests++;
    if (!ok) throw new AssertionError(name);
  }

  private static void point(MapsShare.Result r, double lat, double lon, String name) {
    check(
        r != null && r.exact() && Math.abs(r.lat - lat) < 1e-7 && Math.abs(r.lon - lon) < 1e-7,
        name);
  }

  public static void main(String[] args) throws Exception {
    point(
        MapsShare.parseUrl("https://www.google.com/maps/search/?api=1&query=42.355%2C-71.0656"),
        42.355,
        -71.0656,
        "Maps search point");
    point(
        MapsShare.parseUrl("https://maps.google.com/?q=42.3646875,-71.1366875"),
        42.3646875,
        -71.1366875,
        "Shared coordinate query");
    MapsShare.Result named =
        MapsShare.parseUrl("https://www.google.com/maps/search/?api=1&query=Boston+Common");
    check(
        named != null && !named.exact() && named.text.equals("Boston Common"), "Text is not a pin");
    point(
        MapsShare.parseUrl(
            "https://www.google.com/maps/place/Boston+Common/@42.1,-71.1,17z/data=!4m2!3d42.355!4d-71.0656"),
        42.355,
        -71.0656,
        "Place pin beats camera center");
    point(
        MapsShare.parseUrl(
            "https://www.google.com/maps/place/Boston+Common/data=!3d42.355!4d-71.0656?q=Boston+Common"),
        42.355,
        -71.0656,
        "Precise pin beats fuzzy query");
    check(
        MapsShare.parseUrl("https://www.google.com/maps/@42.355,-71.0656,17z") == null,
        "Camera is never a destination");
    check(
        !MapsShare.parseUrl("https://www.google.com/maps/place/Somewhere/@42,-71,17z").exact(),
        "Place with only camera needs geocoding");
    check(
        !MapsShare.parseUrl(
                "https://www.google.com/maps/place/Somewhere/data=!3d42!4d-71!3d43!4d-72")
            .exact(),
        "Multiple pins are ambiguous");
    point(
        MapsShare.parseUrl(
            "https://www.google.com/maps/dir/?api=1&origin=1,2&destination=42.355,-71.0656"),
        42.355,
        -71.0656,
        "Route uses destination");
    point(
        MapsShare.parseUrl("https://maps.google.com/?saddr=1,2&daddr=42.355,-71.0656"),
        42.355,
        -71.0656,
        "Legacy route uses daddr");
    check(
        MapsShare.parseUrl("https://www.google.com/maps?q=99,999") == null
            || !MapsShare.parseUrl("https://www.google.com/maps?q=99,999").exact(),
        "Out of range coordinates rejected");
    check(
        MapsShare.parseUrl("https://www.google.com/maps/search/?query=place_id:ChIJxxx") == null,
        "Place ID is not an address");
    check(MapsShare.trusted("https://maps.app.goo.gl/Example"), "Google short-link host");
    check(!MapsShare.trusted("https://maps.app.goo.gl.evil.test/a"), "Lookalike host rejected");
    check(!MapsShare.trusted("https://maps.app.goo.gl@evil.test/a"), "User info spoof rejected");
    check(!MapsShare.trusted("http://maps.app.goo.gl/a"), "HTTPS required");
    check(!MapsShare.trusted("https://127.0.0.1/a"), "Local hosts rejected");
    check(!MapsShare.trusted("https://www.google.com:8443/maps"), "Unexpected port rejected");
    check(
        MapsShare.plainText("Boston Common\nhttps://maps.app.goo.gl/example")
            .equals("Boston Common"),
        "Mixed share text");
    check(
        MapsShare.firstUrl("Here: https://maps.app.goo.gl/example.")
            .equals("https://maps.app.goo.gl/example"),
        "Trailing punctuation");
    check(
        MapsShare.resolve("Boston Common, Boston MA").text.equals("Boston Common, Boston MA"),
        "Plain text share");
    point(
        MapsShare.resolve("Boston Common\nhttps://www.google.com/maps?q=42.355,-71.0656"),
        42.355,
        -71.0656,
        "Mixed exact share");
    boolean denied = false;
    try {
      MapsShare.resolve("https://example.org/place");
    } catch (java.io.IOException e) {
      denied = true;
    }
    check(denied, "Unsupported URL rejected before network");
    if (args.length > 0) {
      String live =
          new String(
                  java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),
                  java.nio.charset.StandardCharsets.UTF_8)
              .trim();
      point(
          MapsShare.parseUrl(live),
          42.3275375,
          -71.0672225,
          "Real Google short-link redirect fixture");
    }
    System.out.println(tests + " Maps share checks passed");
  }
}

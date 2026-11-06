import com.orz.ridecompare.MapsShare;
import java.io.*;
import java.net.*;
import java.util.*;

/** Deterministic transport fixtures; no external network requests. */
public class MapsResolveTest {
  static final Queue<Object[]> replies = new ArrayDeque<>();
  static int requests, checks;
  static final java.util.List<String> requestedUrls = new ArrayList<>();

  static void reply(int code, String redirect, String html) {
    replies.add(new Object[] {code, redirect, html});
  }

  static void check(boolean ok, String name) {
    checks++;
    if (!ok) throw new AssertionError(name);
  }

  static void point(MapsShare.Result r, String name) {
    check(r != null && r.exact() && r.lat == 42.355 && r.lon == -71.0656, name);
  }

  public static void main(String[] args) throws Exception {
    URL.setURLStreamHandlerFactory(
        protocol ->
            "https".equals(protocol)
                ? new URLStreamHandler() {
                  protected URLConnection openConnection(URL u) {
                    requests++;
                    requestedUrls.add(u.toString());
                    Object[] r = replies.poll();
                    if (r == null) throw new AssertionError("Unexpected request " + u);
                    return new HttpURLConnection(u) {
                      public void disconnect() {}

                      public boolean usingProxy() {
                        return false;
                      }

                      public void connect() {}

                      public int getResponseCode() throws IOException {
                        if ((int) r[0] == -1) throw new SocketTimeoutException();
                        if ((int) r[0] == -2) throw new UnknownHostException();
                        return (int) r[0];
                      }

                      public String getHeaderField(String key) {
                        return "Location".equals(key) ? (String) r[1] : null;
                      }

                      public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(((String) r[2]).getBytes("UTF-8"));
                      }
                    };
                  }
                }
                : null);
    String exact = "https://www.google.com/maps/place/Boston+Common/data=!3d42.355!4d-71.0656";
    point(
        MapsShare.parseHtml("<meta content='" + exact + "' property='og:url'>"),
        "Content before property");
    point(MapsShare.parseHtml("<link href=\"" + exact + "\" rel=\"canonical\">"), "Canonical URL");
    point(
        MapsShare.parseHtml(
            "<meta content='42.355' property='place:location:latitude'><meta"
                + " property='place:location:longitude' content='-71.0656'>"),
        "Place coordinate metadata");
    check(
        MapsShare.parseHtml(
                "<meta content='https://www.google.com/maps/@42.355,-71.0656,17z'"
                    + " property='og:url'>")
            == null,
        "Viewport metadata rejected");
    point(
        MapsShare.parseUrl("https://www.google.com/url?q=" + URLEncoder.encode(exact, "UTF-8")),
        "Wrapped Maps URL");
    reply(-1, null, "");
    reply(302, exact, "");
    point(MapsShare.resolve("https://maps.app.goo.gl/timeout"), "Timeout retries once");
    reply(503, null, "");
    reply(302, exact, "");
    point(
        MapsShare.resolve("https://maps.app.goo.gl/unavailable"),
        "Transient server response retries");
    reply(302, "https://www.google.com/maps/place/Boston+Common", "");
    reply(200, null, "<meta content='" + exact + "' property='og:url'>");
    point(
        MapsShare.resolve("https://maps.app.goo.gl/place"),
        "Name-only redirect continues to exact page metadata");
    reply(-1, null, "");
    reply(-1, null, "");
    check(
        MapsShare.resolve("Boston Common\nhttps://maps.app.goo.gl/offline")
            .text
            .equals("Boston Common"),
        "Network failure retains shared address text");
    reply(-1, null, "");
    reply(-1, null, "");
    check(
        MapsShare.resolve("https://www.google.com/maps/place/Boston+Common")
            .text
            .equals("Boston Common"),
        "Network failure retains URL place name");
    int before = requests;
    reply(302, "https://example.org/elsewhere", "");
    boolean failed = false;
    try {
      MapsShare.resolve("https://maps.app.goo.gl/untrusted");
    } catch (IOException e) {
      failed = true;
    }
    check(failed && requests == before + 1, "Does not follow unrelated redirect");
    String logan =
        "https://www.google.com/maps/place/Boston+Logan+International+Airport+(BOS),+East+Boston,+MA+02128/data=!4m2!3m1!1s0x89e37014d5da4937:0xc9394c31f2d5144!18m1!1e1";
    reply(302, logan, "");
    reply(200, null, "<html>Generic Maps page without a pin</html>");
    MapsShare.Result loganResult =
        MapsShare.resolve("https://maps.app.goo.gl/logan-fixture?g_st=ac");
    check(
        !loganResult.exact()
            && loganResult.text.equals(
                "Boston Logan International Airport (BOS), East Boston, MA 02128"),
        "Place-ID redirect retains full destination without guessing coordinates");
    reply(200, null, "<a href='" + exact + "'>Open Maps</a>");
    point(MapsShare.resolve("https://maps.app.goo.gl/landing"), "Short-link HTML landing page");
    check(
        MapsShare.parseHtml("<a href='" + exact + "'>Nearby place</a>") == null,
        "Full Maps page anchors cannot define destination");
    check(
        MapsShare.parseHtml(
                "<a href='" + exact + "'>A</a><a href='https://www.google.com/maps?q=43,-72'>B</a>",
                true)
            == null,
        "Ambiguous landing links rejected");
    reply(-1, null, "");
    reply(302, exact, "");
    point(MapsShare.resolve("https://maps.app.goo.gl/retry?g_st=ac"), "Retry link resolves");
    check(
        requestedUrls.get(requestedUrls.size() - 1).equals("https://maps.app.goo.gl/retry"),
        "Retry removes optional short-link tracking query");
    reply(-2, null, "");
    reply(-2, null, "");
    String failure = "";
    try {
      MapsShare.resolve("https://maps.app.goo.gl/dns");
    } catch (IOException e) {
      failure = e.getMessage();
    }
    check(failure.contains("DNS"), "DNS cause preserved for phone diagnosis");
    reply(-1, null, "");
    reply(-1, null, "");
    try {
      MapsShare.resolve("https://maps.app.goo.gl/slow");
    } catch (IOException e) {
      failure = e.getMessage();
    }
    check(failure.contains("超时"), "Timeout cause preserved for phone diagnosis");
    reply(404, null, "");
    reply(302, exact, "");
    point(
        MapsShare.resolve("https://maps.app.goo.gl/missing?g_st=ac"),
        "Transient short-link 404 retries once");
    reply(404, null, "");
    reply(404, null, "");
    failure = "";
    before = requests;
    try {
      MapsShare.resolve("https://maps.app.goo.gl/gone");
    } catch (IOException e) {
      failure = e.getMessage();
    }
    check(
        failure.contains("404") && requests == before + 2,
        "Persistent short-link 404 stops after bounded retry");
    reply(404, null, "");
    before = requests;
    try {
      MapsShare.resolve("https://www.google.com/maps/unknown");
    } catch (IOException ignored) {
    }
    check(requests == before + 1, "Ordinary page 404 does not retry");
    check(replies.isEmpty(), "All transport fixtures consumed");
    System.out.println(checks + " Maps resolver checks passed");
  }
}

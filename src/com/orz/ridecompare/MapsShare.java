package com.orz.ridecompare;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Google Maps share formats. Viewport coordinates deliberately do not define a destination. */
public final class MapsShare {
  private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\\"]+");
  private static final Pattern PAIR =
      Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");
  private static final Pattern PIN =
      Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)");

  public static final class Result {
    public final Double lat, lon;
    public final String text;

    Result(Double a, Double b, String s) {
      lat = a;
      lon = b;
      text = s;
    }

    public boolean exact() {
      return lat != null && lon != null;
    }
  }

  public static boolean trusted(String raw) {
    try {
      URI u = new URI(raw);
      String host = u.getHost();
      return "https".equalsIgnoreCase(u.getScheme())
          && u.getUserInfo() == null
          && (u.getPort() == -1 || u.getPort() == 443)
          && host != null
          && (host.equalsIgnoreCase("maps.app.goo.gl")
              || host.equalsIgnoreCase("goo.gl")
              || host.equalsIgnoreCase("google.com")
              || host.equalsIgnoreCase("www.google.com")
              || host.equalsIgnoreCase("maps.google.com"));
    } catch (Exception e) {
      return false;
    }
  }

  public static String firstUrl(String text) {
    Matcher m = URL.matcher(text);
    return m.find() ? m.group().replaceAll("[).,，。]+$", "") : null;
  }

  public static String plainText(String text) {
    return URL.matcher(text)
        .replaceAll("")
        .replaceAll("(?im)^\\s*(Google Maps|在 Google 地图上查看|在地图上查看)\\s*$", "")
        .trim();
  }

  private static Result coordinates(String value, String label) {
    Matcher m = PAIR.matcher(value);
    if (!m.matches()) return null;
    try {
      double a = Double.parseDouble(m.group(1)), b = Double.parseDouble(m.group(2));
      if (a < -90 || a > 90 || b < -180 || b > 180) return null;
      return new Result(a, b, label);
    } catch (Exception e) {
      return null;
    }
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (Exception e) {
      return value;
    }
  }

  public static Result parseUrl(String raw) {
    return parseUrl(raw, 0);
  }

  private static Result parseUrl(String raw, int depth) {
    if (depth > 3) return null;
    if (!trusted(raw)) return null;
    try {
      URI u = new URI(raw);
      String path = u.getRawPath() == null ? "" : u.getRawPath();
      Map<String, String> params = new HashMap<>();
      String q = u.getRawQuery();
      if (q != null)
        for (String part : q.split("&")) {
          String[] p = part.split("=", 2);
          params.put(decode(p[0]), p.length == 2 ? decode(p[1]) : "");
        }
      for (String key : new String[] {"link", "url", "q"}) {
        String nested = params.get(key);
        if (nested != null && trusted(nested)) {
          Result r = parseUrl(nested, depth + 1);
          if (r != null) return r;
        }
      }
      // Directions use their destination; never use the origin or viewport center.
      boolean route =
          path.contains("/dir") || params.containsKey("destination") || params.containsKey("daddr");
      String target = route ? params.get("destination") : params.get("query");
      if (target == null && !route) target = params.get("q");
      if (target == null) target = params.get("daddr");
      String name = "";
      Matcher place = Pattern.compile("/maps/place/([^/]+)").matcher(path);
      if (place.find()) name = decode(place.group(1));
      if (target != null && !target.trim().isEmpty() && !target.startsWith("place_id:")) {
        Result c = coordinates(target, name.isEmpty() ? target : name);
        if (c != null) return c;
      }
      // A single pin in a place-detail URL is a destination. @lat,lng is only the camera.
      if (path.contains("/place/")) {
        Matcher pin = PIN.matcher(decode(raw));
        Result exact = null;
        int count = 0;
        while (pin.find()) {
          exact = coordinates(pin.group(1) + "," + pin.group(2), name);
          count++;
        }
        if (count == 1 && exact != null) return exact;
        if (!name.isEmpty()) return new Result(null, null, name);
      }
      if (target != null
          && !target.trim().isEmpty()
          && !target.startsWith("place_id:")
          && !target.startsWith("http")) return new Result(null, null, target);
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private static String unescape(String s) {
    return s.replace("&amp;", "&")
        .replace("&#38;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'");
  }

  /** Attribute order varies across the mobile and desktop Maps response. */
  public static Result parseHtml(String html) {
    return parseHtml(html, false);
  }

  public static Result parseHtml(String html, boolean shortLinkPage) {
    Matcher tags =
        Pattern.compile("<(?:meta|link|a)\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
    Result fallback = null;
    String latitude = null, longitude = null;
    Map<String, Result> landingTargets = new HashMap<>();
    while (tags.find()) {
      Map<String, String> attrs = new HashMap<>();
      Matcher a =
          Pattern.compile("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL)
              .matcher(tags.group());
      while (a.find()) attrs.put(a.group(1).toLowerCase(Locale.US), unescape(a.group(3)));
      String key = attrs.containsKey("property") ? attrs.get("property") : attrs.get("name"),
          content = attrs.get("content");
      boolean anchor = tags.group().matches("(?is)<a\\b.*");
      String url =
          "og:url".equals(key)
              ? content
              : (anchor || "canonical".equals(attrs.get("rel")) ? attrs.get("href") : null);
      if (url != null && (!anchor || shortLinkPage)) {
        Result r = parseUrl(url);
        if (r != null) {
          if (anchor) landingTargets.put(r.exact() ? r.lat + "," + r.lon : r.text, r);
          else {
            if (r.exact()) return r;
            fallback = r;
          }
        }
      }
      if ("place:location:latitude".equals(key)) latitude = content;
      if ("place:location:longitude".equals(key)) longitude = content;
    }
    if (latitude != null && longitude != null) {
      Result r = coordinates(latitude + "," + longitude, fallback == null ? "" : fallback.text);
      if (r != null) return r;
    }
    return fallback != null
        ? fallback
        : (landingTargets.size() == 1 ? landingTargets.values().iterator().next() : null);
  }

  public static Result resolve(String shared) throws IOException {
    String url = firstUrl(shared), label = plainText(shared);
    if (url == null) return new Result(null, null, shared.trim());
    if (!trusted(url)) throw new IOException("分享链接不是支持的 Google Maps HTTPS 地址");
    Result parsed = parseUrl(url), fallback = parsed;
    if (parsed != null && parsed.exact()) return useLabel(parsed, label);
    long deadline = System.nanoTime() + 24_000_000_000L;
    int retries = 1;
    String failure = "返回页面中没有可读取的地点";
    for (int hop = 0; hop < 8; hop++) {
      if (System.nanoTime() > deadline) {
        failure = "链接读取超时";
        break;
      }
      if (!trusted(url)) throw new IOException("Maps 跳转到了不支持的地址");
      HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
      int remaining =
          (int) Math.max(1, Math.min(6000, (deadline - System.nanoTime()) / 1_000_000L));
      c.setInstanceFollowRedirects(false);
      c.setUseCaches(false);
      c.setRequestProperty("Cache-Control", "no-cache");
      c.setConnectTimeout(remaining);
      c.setReadTimeout(remaining);
      c.setRequestProperty(
          "User-Agent",
          "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36");
      try {
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
          String next = c.getHeaderField("Location");
          if (next == null) {
            failure = "Maps 跳转缺少目标地址（HTTP " + code + "）";
            break;
          }
          url = new URL(new URL(url), next).toString();
          if (!trusted(url)) {
            failure = "Maps 跳转到了不支持的地址";
            break;
          }
          parsed = parseUrl(url);
          if (parsed != null) {
            if (parsed.exact()) return useLabel(parsed, label);
            fallback = parsed;
          }
          continue;
        }
        if (code != 200) failure = "Maps 返回 HTTP " + code;
        if ((code == 429 || code >= 500 || (code == 404 && isShortLink(url))) && retries-- > 0) {
          url = retryUrl(url);
          continue;
        }
        if (code == 200) {
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          try (InputStream in = c.getInputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while (bytes.size() < 196608 && (n = in.read(buffer)) != -1) {
              bytes.write(buffer, 0, n);
              if (System.nanoTime() > deadline) throw new IOException("Link resolution timeout");
            }
          }
          String html = bytes.toString("UTF-8");
          parsed =
              parseHtml(
                  html,
                  "maps.app.goo.gl".equalsIgnoreCase(new URL(url).getHost())
                      || "goo.gl".equalsIgnoreCase(new URL(url).getHost()));
          if (parsed != null) {
            if (parsed.exact()) return useLabel(parsed, label);
            fallback = parsed;
          }
        }
        break;
      } catch (IOException e) {
        failure = networkFailure(e);
        if (retries-- > 0 && System.nanoTime() < deadline) {
          url = retryUrl(url);
          continue;
        }
        break;
      } finally {
        c.disconnect();
      }
    }
    if (fallback != null) return useLabel(fallback, label);
    if (!label.isEmpty()) return new Result(null, null, label);
    throw new IOException(failure);
  }

  private static boolean isShortLink(String raw) {
    try {
      String host = new URI(raw).getHost();
      return "maps.app.goo.gl".equalsIgnoreCase(host) || "goo.gl".equalsIgnoreCase(host);
    } catch (Exception e) {
      return false;
    }
  }

  private static String retryUrl(String raw) {
    try {
      URI u = new URI(raw);
      return "maps.app.goo.gl".equalsIgnoreCase(u.getHost())
          ? new URI("https", null, u.getHost(), -1, u.getPath(), null, null).toString()
          : raw;
    } catch (Exception e) {
      return raw;
    }
  }

  private static String networkFailure(IOException e) {
    if (e instanceof UnknownHostException) return "无法解析 Maps 域名（DNS）";
    if (e instanceof java.net.SocketTimeoutException) return "连接 Maps 超时";
    if (e instanceof javax.net.ssl.SSLException)
      return "与 Maps 的 HTTPS 连接失败（" + e.getClass().getSimpleName() + "）";
    if (e instanceof java.net.ConnectException) return "无法连接 Maps 服务器";
    return "读取 Maps 失败（" + e.getClass().getSimpleName() + "）";
  }

  private static Result useLabel(Result r, String label) {
    return r.exact() && !label.isEmpty() ? new Result(r.lat, r.lon, label) : r;
  }
}

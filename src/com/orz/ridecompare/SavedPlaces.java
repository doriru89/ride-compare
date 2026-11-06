package com.orz.ridecompare;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Local, bounded address book. Codec is independent of Android for round-trip testing. */
public final class SavedPlaces {
  public static final int MAX_PLACES = 50;
  private final List<Place> places = new ArrayList<>();

  public static final class Place {
    public final String name, address;
    public final double latitude, longitude;

    public Place(String name, String address, double latitude, double longitude) {
      this.name = name == null ? "" : name.trim();
      this.address = address == null ? "" : address.trim();
      if (this.name.isEmpty() || this.name.length() > 40)
        throw new IllegalArgumentException("名称须为 1–40 个字符");
      if (this.address.isEmpty() || this.address.length() > 1200)
        throw new IllegalArgumentException("请先选定有效地址");
      if (!Double.isFinite(latitude)
          || !Double.isFinite(longitude)
          || Math.abs(latitude) > 90
          || Math.abs(longitude) > 180) throw new IllegalArgumentException("地点坐标无效");
      this.latitude = latitude;
      this.longitude = longitude;
    }
  }

  public List<Place> all() {
    return Collections.unmodifiableList(new ArrayList<>(places));
  }

  public void add(Place place) {
    if (places.size() >= MAX_PLACES) throw new IllegalArgumentException("最多保存 50 个常用地址");
    for (Place existing : places)
      if (existing.name.equalsIgnoreCase(place.name))
        throw new IllegalArgumentException("这个名称已存在，请换一个名称");
    places.add(place);
  }

  public void remove(Place place) {
    places.remove(place);
  }

  public String encode() {
    StringBuilder out = new StringBuilder("ride-places-v1\n");
    for (Place p : places)
      out.append(pack(p.name))
          .append('\t')
          .append(pack(p.address))
          .append('\t')
          .append(p.latitude)
          .append('\t')
          .append(p.longitude)
          .append('\n');
    return out.toString();
  }

  public static SavedPlaces decode(String encoded) {
    SavedPlaces result = new SavedPlaces();
    if (encoded == null || encoded.length() > 500_000 || !encoded.startsWith("ride-places-v1\n"))
      return result;
    for (String line : encoded.substring(15).split("\n")) {
      if (result.places.size() >= MAX_PLACES) break;
      String[] parts = line.split("\t", -1);
      if (parts.length != 4) continue;
      try {
        result.add(
            new Place(
                unpack(parts[0]),
                unpack(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3])));
      } catch (IllegalArgumentException ignored) {
        // One damaged record must not hide the remaining saved places.
      }
    }
    return result;
  }

  private static String pack(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String unpack(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }
}

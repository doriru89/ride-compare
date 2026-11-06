import com.orz.ridecompare.SavedPlaces;

public class SavedPlacesTest {
  private static int tests;

  private static void check(boolean ok, String name) {
    tests++;
    if (!ok) throw new AssertionError(name);
  }

  private static void invalid(Runnable action, String name) {
    boolean rejected = false;
    try {
      action.run();
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected, name);
  }

  public static void main(String[] args) {
    SavedPlaces places = new SavedPlaces();
    places.add(new SavedPlaces.Place(" work ", "A public destination\nBoston\tMA", 42.35, -71.06));
    places.add(new SavedPlaces.Place("家 🏠", "中文地址", -33.8, 151.2));
    SavedPlaces restored = SavedPlaces.decode(places.encode());
    check(restored.all().size() == 2, "Both places survive reload");
    check(restored.all().get(0).name.equals("work"), "Names trimmed");
    check(
        restored.all().get(0).address.equals("A public destination\nBoston\tMA"),
        "Delimiters round-trip");
    check(restored.all().get(1).name.equals("家 🏠"), "Unicode names survive reload");
    check(
        restored.all().get(1).latitude == -33.8 && restored.all().get(1).longitude == 151.2,
        "Coordinates preserved");
    invalid(
        () -> places.add(new SavedPlaces.Place("WORK", "Other", 1, 2)),
        "Duplicate name cannot silently overwrite");
    invalid(() -> new SavedPlaces.Place(" ", "Place", 1, 2), "Empty name rejected");
    invalid(() -> new SavedPlaces.Place("home", " ", 1, 2), "Empty address rejected");
    invalid(() -> new SavedPlaces.Place("home", "Place", Double.NaN, 1), "NaN rejected");
    invalid(
        () -> new SavedPlaces.Place("home", "Place", 1, Double.POSITIVE_INFINITY),
        "Infinity rejected");
    invalid(() -> new SavedPlaces.Place("home", "Place", 91, 1), "Latitude bounds");
    invalid(() -> new SavedPlaces.Place("home", "Place", 1, -181), "Longitude bounds");
    String longName = new String(new char[41]).replace('\0', 'x');
    invalid(() -> new SavedPlaces.Place(longName, "Place", 1, 2), "Long name bounded");
    check(
        SavedPlaces.decode(places.encode() + "bad\tdata\tNaN\t1\n").all().size() == 2,
        "Corrupt record does not erase good records");
    check(
        SavedPlaces.decode("unknown-version\n").all().isEmpty(), "Unknown format safely rejected");
    check(SavedPlaces.decode(null).all().isEmpty(), "Fresh install is empty");
    restored.remove(restored.all().get(0));
    check(SavedPlaces.decode(restored.encode()).all().size() == 1, "Deletion survives reload");
    SavedPlaces bounded = new SavedPlaces();
    for (int i = 0; i < SavedPlaces.MAX_PLACES; i++)
      bounded.add(new SavedPlaces.Place("Place " + i, "Address", 1, 2));
    invalid(
        () -> bounded.add(new SavedPlaces.Place("Overflow", "Address", 1, 2)),
        "Address book size bounded");
    boolean readOnly = false;
    try {
      places.all().clear();
    } catch (UnsupportedOperationException e) {
      readOnly = true;
    }
    check(readOnly && places.all().size() == 2, "Callers cannot bypass address validation");
    System.out.println(tests + " saved-place checks passed");
  }
}

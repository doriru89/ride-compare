import com.orz.ridecompare.PickupState;

public class PickupStateTest {
  private static int count;

  private static void check(boolean condition, String label) {
    count++;
    if (!condition) throw new AssertionError(label);
  }

  public static void main(String[] args) {
    PickupState state = new PickupState();
    check(!state.manual(), "Starts at current location");
    check(state.needsGps(false, 0), "Missing current location requests GPS");
    int oldGps = state.useCurrent();
    check(state.acceptsGps(oldGps), "Active GPS result can be used");
    state.editManual();
    check(!state.acceptsGps(oldGps), "Typing a custom pickup rejects in-flight GPS");
    check(
        !state.needsGps(true, 999_000_000_000L), "Confirmed custom pickup never expires into GPS");
    check(!state.needsGps(false, 0), "Unconfirmed manual text never silently falls back to GPS");
    int newGps = state.useCurrent();
    check(
        !state.manual() && state.acceptsGps(newGps),
        "Explicit current-location action restores GPS");
    check(!state.acceptsGps(oldGps), "Old GPS response stays invalid after switching back");
    check(!state.needsGps(true, 120_000_000_000L), "Fresh GPS location is reused");
    check(state.needsGps(true, 120_000_000_001L), "Expired GPS location is refreshed");
    state.editManual();
    state.editManual();
    check(!state.acceptsGps(newGps), "Repeated custom edits still reject old GPS");
    System.out.println(count + " pickup state checks passed");
  }
}

package com.orz.ridecompare;

/** Stops stale GPS callbacks from overriding an explicitly edited pickup. */
public final class PickupState {
  private boolean manual;
  private int revision;

  public int useCurrent() {
    manual = false;
    return ++revision;
  }

  public int editManual() {
    manual = true;
    return ++revision;
  }

  public boolean manual() {
    return manual;
  }

  public boolean acceptsGps(int token) {
    return !manual && token == revision;
  }

  public boolean needsGps(boolean hasPoint, long ageNanos) {
    return !manual && (!hasPoint || ageNanos > 120_000_000_000L);
  }
}

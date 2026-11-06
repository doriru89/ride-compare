package com.orz.ridecompare;

import android.content.Context;
import android.content.SharedPreferences;

/** App-private storage; excluded from cloud backup by the application manifest. */
final class SavedPlacesStore {
  private final SharedPreferences preferences;

  SavedPlacesStore(Context context) {
    preferences = context.getSharedPreferences("saved_places", Context.MODE_PRIVATE);
  }

  SavedPlaces load() {
    return SavedPlaces.decode(preferences.getString("places", ""));
  }

  boolean save(SavedPlaces places) {
    // Small bounded payload. Commit lets the UI report a failed write before closing.
    return preferences.edit().putString("places", places.encode()).commit();
  }
}

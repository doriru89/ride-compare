#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/tests
sources=(src/com/orz/ridecompare/MapsShare.java src/com/orz/ridecompare/PickupState.java src/com/orz/ridecompare/SavedPlaces.java)
if command -v javac >/dev/null; then
  javac -encoding UTF-8 -source 8 -target 8 -d build/tests "${sources[@]}" tests/*.java
else
  : "${ECJ_JAR:?Install JDK 17 or set ECJ_JAR to Eclipse ECJ 3.38.0}"
  java -jar "$ECJ_JAR" -encoding UTF-8 -source 8 -target 8 -d build/tests "${sources[@]}" tests/*.java
fi
for test_class in MapsShareTest MapsResolveTest PickupStateTest SavedPlacesTest; do
  java -cp build/tests "$test_class"
done

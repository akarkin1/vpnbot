package org.github.akarkin1.ui.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionLabelsTest {

  private final RegionLabels labels = new RegionLabels(
      Map.of("eu-central-1", "Frankfurt", "eu-west-2", "London", "us-east-1", "N. Virginia"),
      Map.of("eu-central-1", "DE", "eu-west-2", "D1", "us-east-1", "USA"));

  @Test
  @DisplayName("AC-2: eu-central-1 is labelled with the German flag and Frankfurt")
  void labelCombinesFlagAndCity() {
    assertEquals("🇩🇪 Frankfurt", labels.label("eu-central-1"));
  }

  @Test
  @DisplayName("AC-2: flag is built from regional indicator symbols of the country code")
  void flagFromCountryCode() {
    assertEquals("🇩🇪", labels.flag("eu-central-1"));
  }

  @Test
  @DisplayName("AC-2: city comes from region-cities")
  void cityFromConfig() {
    assertEquals("Frankfurt", labels.city("eu-central-1"));
  }

  @Test
  @DisplayName("AC-2: invalid country code gives the globe")
  void invalidCountryCodeGivesGlobe() {
    assertAll(
        () -> assertEquals("🌐", labels.flag("eu-west-2")),
        () -> assertEquals("🌐", labels.flag("us-east-1")),
        () -> assertEquals("🌐 London", labels.label("eu-west-2")));
  }

  @Test
  @DisplayName("AC-2: unknown region uses its id as city and the globe as flag")
  void unknownRegion() {
    assertAll(
        () -> assertEquals("ap-unknown-1", labels.city("ap-unknown-1")),
        () -> assertEquals("🌐", labels.flag("ap-unknown-1")),
        () -> assertEquals("🌐 ap-unknown-1", labels.label("ap-unknown-1")));
  }

  @Test
  @DisplayName("AC-2: null maps don't fail")
  void nullMaps() {
    RegionLabels empty = new RegionLabels(null, null);

    assertAll(
        () -> assertEquals("eu-central-1", empty.city("eu-central-1")),
        () -> assertEquals("🌐", empty.flag("eu-central-1")),
        () -> assertEquals("🌐 eu-central-1", empty.label("eu-central-1")));
  }

}

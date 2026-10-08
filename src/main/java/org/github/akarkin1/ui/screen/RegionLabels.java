package org.github.akarkin1.ui.screen;

import java.util.Map;
import java.util.Objects;

public class RegionLabels {

  private static final String GLOBE = "🌐";
  private static final int REGIONAL_INDICATOR_A = 0x1F1E6;

  private final Map<String, String> regionCities;
  private final Map<String, String> regionCountries;

  public RegionLabels(Map<String, String> regionCities, Map<String, String> regionCountries) {
    this.regionCities = Objects.requireNonNullElse(regionCities, Map.of());
    this.regionCountries = Objects.requireNonNullElse(regionCountries, Map.of());
  }

  public String city(String regionId) {
    return regionCities.getOrDefault(regionId, regionId);
  }

  public String flag(String regionId) {
    String countryCode = regionCountries.get(regionId);
    if (!isCountryCode(countryCode)) {
      return GLOBE;
    }
    StringBuilder flag = new StringBuilder();
    countryCode.chars().forEach(letter -> flag.appendCodePoint(REGIONAL_INDICATOR_A + letter - 'A'));
    return flag.toString();
  }

  public String label(String regionId) {
    return flag(regionId) + " " + city(regionId);
  }

  private static boolean isCountryCode(String code) {
    return code != null
           && code.length() == 2
           && code.chars().allMatch(letter -> letter >= 'A' && letter <= 'Z');
  }

}

package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class RegionLabels {

  private final Map<String, String> regionCities;
  private final Map<String, String> regionCountries;

  public String city(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public String flag(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public String label(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}

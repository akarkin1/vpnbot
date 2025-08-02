package org.github.akarkin1.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class SupportedRegionDescription {
  private String city;
  private String regionShort;
  private List<String> services;

  @Override
  public String toString() {
    return "%s (%s): %s".formatted(
        city,
        regionShort,
        String.join(", ", services));
  }
}

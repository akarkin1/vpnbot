package org.github.akarkin1.ui.screen;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shared fixtures and assertions for screen tests. Expected buttons are written as literal
 * {@link Button} records so the tests do not depend on how {@code UiAction} encodes itself.
 */
final class ScreenTestSupport {

  static final Button MENU = new Button("🏠 ${ui.button.menu}", "HOME", null);
  static final List<Button> LINKS = List.of(
      new Button("📖 ${ui.button.exit-node-guide}", null, Links.EXIT_NODE_GUIDE),
      new Button("⬇️ ${ui.button.get-tailscale}", null, Links.DOWNLOAD));
  static final List<Button> REFRESH_HELP = List.of(
      new Button("🔄 ${ui.button.refresh}", "HOME", null),
      new Button("❓ ${ui.button.help}", "HELP", null));

  private ScreenTestSupport() {
  }

  static RegionLabels regionLabels() {
    return new RegionLabels(
        Map.of("eu-central-1", "Frankfurt",
               "eu-west-2", "London",
               "us-east-1", "N. Virginia",
               "ap-northeast-1", "Tokyo",
               "eu-north-1", "Stockholm",
               "sa-east-1", "Sao Paulo",
               "ap-south-1", "mumbai"),
        Map.of("eu-central-1", "DE",
               "eu-west-2", "GB",
               "us-east-1", "US",
               "ap-northeast-1", "JP",
               "eu-north-1", "SE",
               "sa-east-1", "BR",
               "ap-south-1", "IN"));
  }

  /** AC-7: the number of {@code %s} in the template equals the number of params, none is null. */
  static void assertPlaceholdersMatchParams(Screen screen) {
    int placeholders = countPlaceholders(screen.template());
    assertEquals(placeholders, screen.params().size(),
                 "placeholders vs params in template: " + screen.template());
    screen.params().forEach(param -> assertNotNull(param, "null param in " + screen.params()));
  }

  private static int countPlaceholders(String template) {
    int count = 0;
    int index = template.indexOf("%s");
    while (index >= 0) {
      count++;
      index = template.indexOf("%s", index + 2);
    }
    return count;
  }

}

package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.MENU_ROW;
import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** cleanup-2026-10 §4.1: /supportedRegions lists the region descriptions of the node service. */
@ExtendWith(MockitoExtension.class)
class SupportedRegionCommandTest {

  private static final UiContext CONTEXT = new UiContext(100L, 7L, "alex", "Alex", null, "en");
  private static final List<String> REGIONS = List.of(
      "eu-central-1 (Frankfurt)", "us-east-1 (N. Virginia <&>)");

  @Mock
  private TailscaleNodeService nodeService;

  private SupportedRegionCommand command;

  @BeforeEach
  void setUp() {
    command = new SupportedRegionCommand(nodeService);
  }

  @Test
  @DisplayName("§4.1: the reply lists every supported region after ${command.supported-regions.supported-regions.message}, with the Menu row")
  void listsRegions() {
    when(nodeService.getSupportedRegionDescriptions()).thenReturn(REGIONS);

    Screen screen = command.run(CONTEXT, List.of());

    String text = text(screen);
    int title = text.indexOf("${command.supported-regions.supported-regions.message}");
    int first = text.indexOf(REGIONS.get(0));
    int second = text.indexOf(REGIONS.get(1));
    assertTrue(title >= 0 && title < first && first < second, text);
    assertFalse(text.contains("${command.supported-regions.no-supported-regions.message}"), text);
    assertEquals(MENU_ROW, screen.keyboard());
  }

  @Test
  @DisplayName("§4.1, CLAUDE.md: each region description is a param (HTML-escaped by the messenger), not part of the template")
  void regionDescriptionsAreParams() {
    when(nodeService.getSupportedRegionDescriptions()).thenReturn(REGIONS);

    Screen screen = command.run(CONTEXT, List.of());

    for (String region : REGIONS) {
      assertTrue(screen.params().contains(region), "params: " + screen.params());
      assertFalse(screen.template().contains(region), "template: " + screen.template());
    }
  }

  @Test
  @DisplayName("§4.1: without supported regions the reply is ${command.supported-regions.no-supported-regions.message}, with the Menu row")
  void noRegions() {
    when(nodeService.getSupportedRegionDescriptions()).thenReturn(List.of());

    Screen screen = command.run(CONTEXT, List.of());

    assertTrue(screen.template().contains("${command.supported-regions.no-supported-regions.message}"),
               screen.template());
    assertFalse(screen.template().contains("${command.supported-regions.supported-regions.message}"),
                screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(MENU_ROW, screen.keyboard());
  }

  @Test
  @DisplayName("§4.1: /supportedRegions requires SUPPORTED_REGIONS and has its description key")
  void permissionAndDescription() {
    assertEquals(List.of(Permission.SUPPORTED_REGIONS), command.getRequiredPermissions());
    assertEquals("${command.supported-regions.description.message}", command.getDescription());
  }

}

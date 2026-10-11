package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.translation.MainMessagesTranslator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.MENU_ROW;
import static org.github.akarkin1.dispatcher.SystemCommandTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * cleanup-2026-10 §4.1, §4.7 rule 5: /help is the app description, a newline and the
 * {@code CommandCatalog}'s list of the commands the caller may use. The catalog is given as a
 * lambda, so the test needs neither the dispatcher nor the catalog's package.
 */
@ExtendWith(MockitoExtension.class)
class HelpCommandTest {

  private static final UiContext CONTEXT = new UiContext(100L, 7L, "alex", "Alex", null, "ru");
  private static final String LIST = "  /help – ${command.help.description.message}\n  /x – ${x.description}";

  @Mock
  private Authorizer authorizer;
  @Mock
  private UiMessenger messenger;
  @Mock
  private TailscaleNodeService nodeService;

  @Test
  @DisplayName("§4.1: the help text is ${command.help.app-description.message}, a newline and the catalog's list for context.user(), with the Menu row")
  void helpText() {
    List<TgUser> askedFor = new ArrayList<>();
    HelpCommand help = new HelpCommand(user -> {
      askedFor.add(user);
      return LIST;
    });

    Screen screen = help.run(CONTEXT, List.of());

    assertEquals("${command.help.app-description.message}\n" + LIST, text(screen));
    assertEquals(List.of(CONTEXT.user()), askedFor);
    assertEquals(MENU_ROW, screen.keyboard());
  }

  @Test
  @DisplayName("§4.1: /help ignores arguments")
  void ignoresArguments() {
    HelpCommand help = new HelpCommand(user -> LIST);

    assertEquals(text(help.run(CONTEXT, List.of())), text(help.run(CONTEXT, List.of("extra"))));
  }

  @Test
  @DisplayName("§4.1: /help needs no permission and has its description key")
  void permissionAndDescription() {
    HelpCommand help = new HelpCommand(user -> LIST);

    assertEquals(List.of(), help.getRequiredPermissions());
    assertEquals("${command.help.description.message}", help.getDescription());
  }

  @Test
  @DisplayName("§4.1, D-3: in Russian, /help lists exactly the four system commands with their descriptions (real message files)")
  void translatedSystemCommands() {
    when(authorizer.hasPermission(any(), any(Permission.class))).thenReturn(true);
    CommandDispatcher dispatcher = new CommandDispatcher(messenger, authorizer);
    dispatcher.registerCommand("/version", new VersionCommand("2026.10-SNAPSHOT"));
    dispatcher.registerCommand("/supportedRegions", new SupportedRegionCommand(nodeService));
    dispatcher.registerCommand("/describeRoles", new DescribeRolesCommand());
    MainMessagesTranslator translator = new MainMessagesTranslator();

    Screen screen = new HelpCommand(dispatcher).run(CONTEXT, List.of());
    String translated = translator.translate("ru", screen.template(), screen.params().toArray())
        .replace(System.lineSeparator(), "\n");

    List<String> lines = translated.lines().toList();
    assertEquals(translator.value("ru", "command.help.app-description.message"), lines.get(0));
    List<List<String>> expected = List.of(
        List.of("/help", translator.value("ru", "command.help.description.message")),
        List.of("/version", translator.value("ru", "command.version.description.message")),
        List.of("/supportedRegions", translator.value("ru", "command.supported-regions.description.message")),
        List.of("/describeRoles", translator.value("ru", "command.describe-roles.description.message")));
    assertEquals(expected.size() + 1, lines.size(), translated);
    for (int i = 0; i < expected.size(); i++) {
      String line = lines.get(i + 1);
      assertTrue(line.contains(expected.get(i).get(0)) && line.contains(expected.get(i).get(1)),
                 "line " + (i + 1) + ": " + line);
    }
    assertFalse(translated.contains("${"), "untranslated key in: " + translated);
  }

}

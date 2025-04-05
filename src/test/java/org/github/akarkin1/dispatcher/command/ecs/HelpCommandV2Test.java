package org.github.akarkin1.dispatcher.command.ecs;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.s3.PermissionsService;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.github.akarkin1.translation.ResourceBasedTranslator;
import org.github.akarkin1.translation.Translator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpCommandV2Test {

  @Mock
  private BotCommunicator botCommunicator;

  @Mock
  private TailscaleNodeService nodeService;

  @Mock
  private PermissionsService permissionsService;

  @Mock
  private Authorizer authorizer;

  private CommandDispatcherV2 commandDispatcherV2;
  private HelpCommandV2 helpCommandV2;

  @BeforeEach
  void setUpHelpCommand() {
    commandDispatcherV2 = new CommandDispatcherV2(botCommunicator, authorizer);

    commandDispatcherV2.registerCommand("/version", new VersionCommandV2());
    commandDispatcherV2.registerCommand("/listRunningNodes", new ListNodesCommand(
      nodeService, authorizer));
    commandDispatcherV2.registerCommand("/runNodeIn",
                                        new RunNodeCommand(nodeService,
                                                           botCommunicator::sendMessageToTheBot));
    commandDispatcherV2.registerCommand("/supportedRegions",
                                        new SupportedRegionCommand(nodeService));
    commandDispatcherV2.registerCommand("/assignRoles",
                                        new AssignRolesCommand(permissionsService));
    commandDispatcherV2.registerCommand("/describeRoles",
                                        new DescribeRolesCommand(permissionsService));
    commandDispatcherV2.registerCommand("/deleteUsers",
                                        new DeleteUsersCommand(permissionsService,
                                                               botCommunicator::sendMessageToTheBot));
    commandDispatcherV2.registerCommand("/listRegisteredUsers",
                                        new ListUsersCommand(permissionsService));

    helpCommandV2 = new HelpCommandV2(commandDispatcherV2);
  }

  @Test
  void testTranslation() {
    try (MockedStatic<TgRequestContext> mockedContext = Mockito.mockStatic(
      TgRequestContext.class)) {

      mockedContext.when(TgRequestContext::getLanguageCode).thenReturn("ru-RU");
      mockedContext.when(TgRequestContext::getUsername).thenReturn("alex");

      when(authorizer.hasPermission(anyString(), any(Permission.class)))
        .thenReturn(true);

      Translator translator = new ResourceBasedTranslator();
      TextCommandResponse response = helpCommandV2.run(Collections.emptyList());
      String translatedValue = translator.translate("ru-RU", response.text(), response.params());

      // ToDo: Update, once the rest of the commands translated.
      String expectedValue = """
        Бот позволяет управлять нодами Tailscale VPN, развернутыми в AWS. Больше информации о Tailscale можно узнатьпо ссылке: https://tailscale.com/. Чтобы подключиться к запущенной ноде, вам нужно зарегистрироваться в Tailscale и запросить доступ к сети Tailscale. Для последнего, пожалуйста, свяжитесь с @karkin_ai. Если нода работает более 10 минут без активного подключения, она будет автоматически остановлена. Список поддерживаемых команд:
          /help – Выводит описание бота.
          /version – Возвращает версию серверного приложения.
          /listRunningNodes – Показывает список нод Tailscale, запущенных пользователем.
          /runNodeIn – Запускает нод Tailscale VPN в указанном регионе.
        ИСПОЛЬЗОВАНИЕ: /runNodeIn RegionName [NodeName],
        где
         - RegionName – это либо идентификатор региона в AWS (напр. eu-north-1), либо название города (на англ.), где будет запущена нода.
         - NodeName (опционально) – имя хоста ноды Tailscale (должно быть уникальным среди всех запущенных нод).
          /supportedRegions – Показывает список поддерживаемых регионов (где настроена инфраструктура Tailscale).
          /assignRoles – Назначает список ролей указанному пользователю.
        ИСПОЛЬЗОВАНИЕ: /assignRoles <TelegramUsername> <UserRoleList>,где:
          - <TelegramUsername> – имя пользователя в Telegram, которому будет назначена роль.
          - <UserRoleList> – список ролей пользователя, разделенных пробелом. Каждая роль может принимать одно из следующих значений: [NODE_ADMIN, USER_ADMIN, READ_ONLY]
          /describeRoles – Возвращает список всех ролей, которые могут быть назначены пользователю.
          /deleteUsers – Удаляет пользователей по перечисленным именам в Telegram.
            ИСПОЛЬЗОВАНИЕ: /deleteUsers <TelegramUserNames>
          /listRegisteredUsers – Возвращает список зарегистрированных пользователей с их ролями""";

      assertEquals(expectedValue, translatedValue);
    }
  }

}
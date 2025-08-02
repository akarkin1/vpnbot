package org.github.akarkin1.dispatcher.command.ecs;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.s3.EntitlementsService;
import org.github.akarkin1.dispatcher.command.AssignRolesCommand;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.command.DeleteUsersCommand;
import org.github.akarkin1.dispatcher.command.DescribeRolesCommand;
import org.github.akarkin1.dispatcher.command.HelpCommand;
import org.github.akarkin1.dispatcher.command.ListNodesCommand;
import org.github.akarkin1.dispatcher.command.ListUsersCommand;
import org.github.akarkin1.dispatcher.command.RunNodeCommand;
import org.github.akarkin1.dispatcher.command.SupportedRegionCommand;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.dispatcher.command.VersionCommand;
import org.github.akarkin1.service.NodeService;
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
  private NodeService nodeService;

  @Mock
  private EntitlementsService entitlementsService;

  @Mock
  private Authorizer authorizer;

  private CommandDispatcher commandDispatcher;
  private HelpCommand helpCommandV2;

  @BeforeEach
  void setUpHelpCommand() {
    commandDispatcher = new CommandDispatcher(botCommunicator, authorizer);

    commandDispatcher.registerCommand("/version", new VersionCommand());
    commandDispatcher.registerCommand("/listRunningNodes", new ListNodesCommand(
      nodeService, authorizer));
    commandDispatcher.registerCommand("/runNodeIn",
                                      new RunNodeCommand(nodeService,
                                                           authorizer,
                                                           botCommunicator::sendMessageToTheBot));
    commandDispatcher.registerCommand("/supportedRegions",
                                      new SupportedRegionCommand(nodeService, authorizer));
    commandDispatcher.registerCommand("/assignRoles",
                                      new AssignRolesCommand(entitlementsService));
    commandDispatcher.registerCommand("/describeRoles",
                                      new DescribeRolesCommand(entitlementsService));
    commandDispatcher.registerCommand("/deleteUsers",
                                      new DeleteUsersCommand(entitlementsService,
                                                               botCommunicator::sendMessageToTheBot));
    commandDispatcher.registerCommand("/listRegisteredUsers",
                                      new ListUsersCommand(entitlementsService));

    helpCommandV2 = new HelpCommand(commandDispatcher);
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
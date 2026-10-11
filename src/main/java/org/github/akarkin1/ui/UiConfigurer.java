package org.github.akarkin1.ui;

import org.github.akarkin1.auth.AccessRequestService;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.DynamoDbAccessRequestService;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.dynamodb.AccessRequestRecord;
import org.github.akarkin1.metrics.RequestMetrics;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.controller.AccessController;
import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.controller.NodeAccess;
import org.github.akarkin1.ui.controller.NodeController;
import org.github.akarkin1.ui.controller.UsersController;
import org.github.akarkin1.ui.messenger.NodeNotifications;
import org.github.akarkin1.ui.messenger.ScreenRenderer;
import org.github.akarkin1.ui.messenger.TelegramUiMessenger;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.AccessScreens;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.ui.screen.HelpScreen;
import org.github.akarkin1.ui.screen.HomeScreen;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.NodeScreens;
import org.github.akarkin1.ui.screen.RegionLabels;
import org.github.akarkin1.ui.screen.UsersScreen;
import org.telegram.telegrambots.meta.bots.AbsSender;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Clock;

public class UiConfigurer {

  public UiComponents configure(AbsSender sender, Translator translator,
                                TailscaleNodeService nodeService, Authorizer authorizer,
                                PermissionsService permissionsService,
                                DynamoDbTable<AccessRequestRecord> accessRequestsTable,
                                RequestMetrics metrics) {
    AWSConfiguration awsConfig = ConfigManager.getApplicationYaml().getAws();
    RegionLabels regionLabels = new RegionLabels(awsConfig.getRegionCities(),
                                                 awsConfig.getRegionCountries());

    ScreenRenderer renderer = new ScreenRenderer(translator);
    UiMessenger messenger = new TelegramUiMessenger(sender, renderer, metrics);
    NodeAccess nodeAccess = new NodeAccess(authorizer);
    HomeController homeController = new HomeController(nodeService, authorizer, nodeAccess,
                                                       messenger, new HomeScreen(regionLabels),
                                                       new HelpScreen());
    LaunchScreens launchScreens = new LaunchScreens(regionLabels);
    LaunchController launchController = new LaunchController(
        nodeService, authorizer, messenger, launchScreens,
        new NodeNotifications(launchScreens, renderer));
    NodeController nodeController = new NodeController(nodeService, nodeAccess, messenger,
                                                       new NodeScreens(),
                                                       launchScreens);

    AccessRequestService accessRequests = new DynamoDbAccessRequestService(
        accessRequestsTable, Clock.systemUTC(), metrics);
    AccessController accessController = new AccessController(
        accessRequests, permissionsService, authorizer, homeController, messenger,
        new AccessScreens(), Clock.systemUTC());
    UsersController usersController = new UsersController(permissionsService, authorizer,
                                                          messenger, new UsersScreen());

    UiRouter router = new UiRouter(homeController, launchController, nodeController,
                                   accessController, usersController, messenger,
                                   new ErrorScreen());
    return new UiComponents(router, messenger);
  }

}

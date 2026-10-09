package org.github.akarkin1.ui;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.metrics.RequestMetrics;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.messenger.NodeNotifications;
import org.github.akarkin1.ui.messenger.ScreenRenderer;
import org.github.akarkin1.ui.messenger.TelegramUiMessenger;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.ui.screen.HelpScreen;
import org.github.akarkin1.ui.screen.HomeScreen;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.RegionLabels;
import org.telegram.telegrambots.meta.bots.AbsSender;

public class UiConfigurer {

  public UiComponents configure(AbsSender sender, Translator translator,
                                TailscaleNodeService nodeService, Authorizer authorizer,
                                RequestMetrics metrics) {
    AWSConfiguration awsConfig = ConfigManager.getApplicationYaml().getAws();
    RegionLabels regionLabels = new RegionLabels(awsConfig.getRegionCities(),
                                                 awsConfig.getRegionCountries());

    ScreenRenderer renderer = new ScreenRenderer(translator);
    UiMessenger messenger = new TelegramUiMessenger(sender, renderer, metrics);
    HomeController homeController = new HomeController(nodeService, authorizer, messenger,
                                                       new HomeScreen(regionLabels),
                                                       new HelpScreen());
    LaunchScreens launchScreens = new LaunchScreens(regionLabels);
    LaunchController launchController = new LaunchController(
        nodeService, authorizer, messenger, launchScreens,
        new NodeNotifications(launchScreens, renderer));

    UiRouter router = new UiRouter(homeController, launchController, messenger, new ErrorScreen());
    return new UiComponents(router, launchController);
  }

}

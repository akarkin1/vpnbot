package org.github.akarkin1.ui;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.messenger.TelegramUiMessenger;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.ui.screen.HelpScreen;
import org.github.akarkin1.ui.screen.HomeScreen;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.RegionLabels;
import org.telegram.telegrambots.meta.bots.AbsSender;

public class UiConfigurer {

  public UiRouter configure(AbsSender sender, Translator translator,
                            TailscaleNodeService nodeService, Authorizer authorizer) {
    AWSConfiguration awsConfig = ConfigManager.getApplicationYaml().getAws();
    RegionLabels regionLabels = new RegionLabels(awsConfig.getRegionCities(),
                                                 awsConfig.getRegionCountries());

    UiMessenger messenger = new TelegramUiMessenger(sender, translator);
    HomeController homeController = new HomeController(nodeService, authorizer, messenger,
                                                       new HomeScreen(regionLabels),
                                                       new HelpScreen());
    LaunchController launchController = new LaunchController(nodeService, authorizer, messenger,
                                                             new LaunchScreens(regionLabels));

    return new UiRouter(homeController, launchController, messenger, new ErrorScreen());
  }

}

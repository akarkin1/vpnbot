package org.github.akarkin1;

import org.github.akarkin1.auth.AccessRequestService;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.CachingPermissionsService;
import org.github.akarkin1.auth.DynamoDbAccessRequestService;
import org.github.akarkin1.auth.DynamoDbPermissionsService;
import org.github.akarkin1.auth.PendingUserActivator;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.RequestAuthenticator;
import org.github.akarkin1.auth.SecretManagerRequestAuthenticator;
import org.github.akarkin1.auth.WhiteListAuthorizer;
import org.github.akarkin1.config.BotTokenResolver;
import org.github.akarkin1.config.CachedTaskConfigService;
import org.github.akarkin1.config.DynamoDbTaskConfigService;
import org.github.akarkin1.config.Settings;
import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.deduplication.DynamoDbUpdateEventsRegistry;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.SystemCommandDispatcher;
import org.github.akarkin1.dispatcher.command.DescribeRolesCommand;
import org.github.akarkin1.dispatcher.command.HelpCommand;
import org.github.akarkin1.dispatcher.command.SupportedRegionCommand;
import org.github.akarkin1.dispatcher.command.VersionCommand;
import org.github.akarkin1.dynamodb.ConfigTables;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.ecs.EcsClientPool;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsManagerImpl;
import org.github.akarkin1.metrics.EmfRequestMetrics;
import org.github.akarkin1.metrics.RequestMetrics;
import org.github.akarkin1.startup.SnapStartPrimer;
import org.github.akarkin1.tailscale.TailscaleEcsNodeService;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TelegramBotFactory;
import org.github.akarkin1.translation.ResourceBasedTranslator;
import org.github.akarkin1.ui.UiRouter;
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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The composition root: builds the whole object graph with plain constructors from the
 * {@link Externals}, once per container (docs/specs/cleanup-2026-10.md §4.7). Every object is a
 * singleton; no constructor does work, {@link #prime()} is the only step after construction.
 */
public final class BotDependencies {

  private static final int ECS_THREADS = 8;

  private final Settings settings;
  private final RequestMetrics metrics;
  private final RequestAuthenticator requestAuthenticator;
  private final UpdateEventsRegistry updateEvents;
  private final UiMessenger messenger;
  private final UiRouter router;
  private final SystemCommandDispatcher systemCommands;
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private final PendingUserActivator pendingUserActivator;
  private final SnapStartPrimer primer;

  /** The bot over the real AWS and Telegram clients, configured from the environment, primed. */
  public static BotDependencies build() {
    Settings settings = Settings.fromEnvironment();
    Externals externals = new Externals(
        DynamoDbEnhancedClient.builder()
            .dynamoDbClient(DynamoDbClient.create())
            .build(),
        SecretsManagerClient.create(),
        token -> TelegramBotFactory.sender(token, settings.botUsername()),
        Clock.systemUTC(),
        settings,
        region -> EcsClient.builder().region(region).build(),
        region -> Ec2Client.builder().region(region).build());
    BotDependencies bot = new BotDependencies(externals);
    bot.prime();
    return bot;
  }

  BotDependencies(Externals externals) {
    Settings settings = externals.settings();
    Clock clock = externals.clock();
    this.settings = settings;
    this.metrics = new EmfRequestMetrics(settings.metricsEnabled(), clock, System.out::println);
    this.requestAuthenticator = requestAuthenticator(externals.secrets(), settings, clock);

    ConfigTables tables = ConfigTables.create(externals.dynamoDb(), settings.configTableName());
    this.updateEvents = new DynamoDbUpdateEventsRegistry(tables.updateLocks(), clock, metrics);
    AbsSender sender = sender(externals);

    Ec2ClientPool ec2ClientPool = new Ec2ClientPool(externals.ec2Clients());
    TailscaleNodeService nodeService = nodeService(tables, externals, ec2ClientPool, metrics);

    PermissionsService permissionsService = permissionsService(tables, settings, clock, metrics);
    Authorizer authorizer = new WhiteListAuthorizer(settings.auth(), permissionsService);
    this.pendingUserActivator = pendingUserActivator(permissionsService);

    ScreenRenderer renderer = new ScreenRenderer(new ResourceBasedTranslator());
    this.messenger = new TelegramUiMessenger(sender, renderer, metrics);
    this.router = router(settings, clock, metrics, tables, renderer, messenger, nodeService,
                         authorizer, permissionsService);
    this.systemCommands = systemCommands(settings, messenger, authorizer, nodeService);

    this.primer = new SnapStartPrimer(nodeService, ec2ClientPool, permissionsService, updateEvents,
                                      sender);
  }

  public UiRouter router() {
    return router;
  }

  public SystemCommandDispatcher systemCommands() {
    return systemCommands;
  }

  public UpdateEventsRegistry updateEvents() {
    return updateEvents;
  }

  /** For the handler's error path. */
  public UiMessenger messenger() {
    return messenger;
  }

  public RequestMetrics metrics() {
    return metrics;
  }

  /** Authenticates API Gateway requests (the webhook secret token). */
  public RequestAuthenticator requestAuthenticator() {
    return requestAuthenticator;
  }

  /** Activates a user pre-registered by username (deprecated, §4.3) at their first request. */
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  public PendingUserActivator pendingUserActivator() {
    return pendingUserActivator;
  }

  /** The settings the graph was built from (the handler's health check shows the version). */
  public Settings settings() {
    return settings;
  }

  /** Runs every request path once so that SnapStart snapshots it; failures are only logged. */
  public void prime() {
    primer.prime();
  }

  // auth

  private static RequestAuthenticator requestAuthenticator(SecretsManagerClient secrets,
                                                           Settings settings, Clock clock) {
    return new SecretManagerRequestAuthenticator(secrets, settings.webhookSecretTokenId(),
                                                 settings.configCacheTtl(), clock);
  }

  /** The USER and PENDING_USER records, cached when the config cache is enabled. */
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private static PermissionsService permissionsService(ConfigTables tables, Settings settings,
                                                       Clock clock, RequestMetrics metrics) {
    PermissionsService permissionsService = new DynamoDbPermissionsService(
        tables.users(), tables.pendingUsers(), metrics);
    if (!settings.configCacheEnabled()) {
      return permissionsService;
    }
    return new CachingPermissionsService(permissionsService, settings.configCacheTtl(), clock);
  }

  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private static PendingUserActivator pendingUserActivator(PermissionsService permissionsService) {
    return new PendingUserActivator(permissionsService);
  }

  // tailscale (ECS, EC2, region config)

  private static TailscaleNodeService nodeService(ConfigTables tables, Externals externals,
                                                  Ec2ClientPool ec2ClientPool,
                                                  RequestMetrics metrics) {
    Settings settings = externals.settings();
    ExecutorService executor = Executors.newFixedThreadPool(
        ECS_THREADS, Thread.ofPlatform().daemon().factory());
    EcsManager ecsManager = new EcsManagerImpl(
        taskConfigService(tables, settings, externals.clock(), metrics),
        new EcsClientPool(externals.ecsClients()), ec2ClientPool, settings.ecs(),
        settings.aws().getRegionCities(), executor, metrics);
    return new TailscaleEcsNodeService(ecsManager, settings.ecs(), settings.aws());
  }

  private static TaskConfigService taskConfigService(ConfigTables tables, Settings settings,
                                                     Clock clock, RequestMetrics metrics) {
    TaskConfigService configService = new DynamoDbTaskConfigService(tables.regions(), metrics);
    if (!settings.configCacheEnabled()) {
      return configService;
    }
    return new CachedTaskConfigService(configService, settings.configCacheTtl(), clock);
  }

  // tg

  private static AbsSender sender(Externals externals) {
    String botToken = new BotTokenResolver(externals.secrets())
        .resolve(externals.settings().botTokenSecretId());
    return externals.senderFactory().apply(botToken);
  }

  // ui

  private static UiRouter router(Settings settings, Clock clock, RequestMetrics metrics,
                                 ConfigTables tables, ScreenRenderer renderer,
                                 UiMessenger messenger, TailscaleNodeService nodeService,
                                 Authorizer authorizer, PermissionsService permissionsService) {
    RegionLabels regionLabels = new RegionLabels(settings.aws().getRegionCities(),
                                                 settings.aws().getRegionCountries());
    NodeAccess nodeAccess = new NodeAccess(authorizer);
    HomeController homeController = new HomeController(nodeService, authorizer, nodeAccess,
                                                       messenger, new HomeScreen(regionLabels),
                                                       new HelpScreen());
    LaunchScreens launchScreens = new LaunchScreens(regionLabels);
    LaunchController launchController = new LaunchController(
        nodeService, authorizer, messenger, launchScreens,
        new NodeNotifications(launchScreens, renderer));
    NodeController nodeController = new NodeController(nodeService, nodeAccess, messenger,
                                                       new NodeScreens(), launchScreens);
    AccessController accessController = new AccessController(
        accessRequests(tables, clock, metrics), permissionsService, authorizer, homeController,
        messenger, new AccessScreens(), clock);
    UsersController usersController = new UsersController(permissionsService, authorizer,
                                                          messenger, new UsersScreen());
    return new UiRouter(homeController, launchController, nodeController, accessController,
                        usersController, messenger, new ErrorScreen());
  }

  private static AccessRequestService accessRequests(ConfigTables tables, Clock clock,
                                                     RequestMetrics metrics) {
    return new DynamoDbAccessRequestService(tables.accessRequests(), clock, metrics);
  }

  // dispatcher

  /** The system commands; {@code /help} lists the others, so it is registered first. */
  private static SystemCommandDispatcher systemCommands(Settings settings, UiMessenger messenger,
                                                        Authorizer authorizer,
                                                        TailscaleNodeService nodeService) {
    SystemCommandDispatcher dispatcher = new SystemCommandDispatcher(messenger, authorizer);
    dispatcher.registerCommand("/help", new HelpCommand(dispatcher));
    dispatcher.registerCommand("/version", new VersionCommand(settings.appVersion()));
    dispatcher.registerCommand("/supportedRegions", new SupportedRegionCommand(nodeService));
    dispatcher.registerCommand("/describeRoles", new DescribeRolesCommand());
    return dispatcher;
  }

}

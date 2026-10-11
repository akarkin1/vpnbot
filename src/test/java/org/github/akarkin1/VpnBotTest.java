package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.apache.logging.log4j.LogManager;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.RequestAuthenticator;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.auth.UnauthenticatedRequestException;
import org.github.akarkin1.config.MainApplicationYaml;
import org.github.akarkin1.config.Settings;
import org.github.akarkin1.config.YamlApplicationConfiguration;
import org.github.akarkin1.dynamodb.FakeTable;
import org.github.akarkin1.dynamodb.RegionRecord;
import org.github.akarkin1.dynamodb.TgUpdateLock;
import org.github.akarkin1.dynamodb.UserRecord;
import org.github.akarkin1.metrics.EmfRequestMetrics;
import org.github.akarkin1.ui.UiContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * cleanup-2026-10 §4.7 rule 7, AC-9: the wiring test. {@link VpnBot} is built from
 * {@link Externals} whose clients are Mockito mocks answering with empty results (the DynamoDB
 * tables are {@link FakeTable}s, Secrets Manager returns fake secrets, the Telegram sender answers
 * {@code getMe} and {@code sendMessage}) and a fixed {@link Settings} with the sections of the real
 * {@code application.yml}. The externals are the only clients the graph gets, so nothing here can
 * reach the network: every request the graph makes shows up in one of the recording lists below.
 * The user table holds one root user and the region table one configured region, so that the
 * permission checks and the per-region priming steps run through the real classes.
 */
// The tables of the deprecated PENDING_USER records are created too. Pre-registration by username
// ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER);
// see docs/specs/cleanup-2026-10.md §4.3.
@SuppressWarnings("removal")
class VpnBotTest {

  private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
  private static final String TABLE_NAME = "vpnbot-test";
  private static final String BOT_TOKEN_SECRET_ID = "bot-token-secret";
  private static final String BOT_TOKEN = "123456:bot-token";
  private static final String SECRET_TOKEN_ID = "webhook-secret";
  private static final String WEBHOOK_SECRET = "webhook-secret-value";
  private static final String BOT_USERNAME = "vpn_bot";
  private static final String APP_VERSION = "2026.10-test";
  private static final Region REGION = Region.EU_CENTRAL_1;

  private static final UiContext ROOT_CONTEXT = new UiContext(10L, 1L, "root", "Root", null, "en");
  private static final TgUser ROOT = ROOT_CONTEXT.user();
  private static final TgUser STRANGER = new TgUser(2L, "stranger");
  /** §4.1: the system commands in the order of the /help list (as before the clean-up). */
  private static final List<String> SYSTEM_COMMANDS = List.of(
      "/help", "/version", "/supportedRegions", "/describeRoles");

  private final Map<Class<?>, FakeTable<?>> tables = new ConcurrentHashMap<>();
  private final List<String> tableNames = new CopyOnWriteArrayList<>();
  private final List<String> secretIds = new CopyOnWriteArrayList<>();
  private final List<String> senderTokens = new CopyOnWriteArrayList<>();
  private final List<BotApiMethod<?>> telegramCalls = new CopyOnWriteArrayList<>();
  private final List<Region> ecsRegions = new CopyOnWriteArrayList<>();
  private final List<Region> ec2Regions = new CopyOnWriteArrayList<>();
  private final List<String> ecsCalls = new CopyOnWriteArrayList<>();
  private final List<String> ec2Calls = new CopyOnWriteArrayList<>();

  private final AbsSender sender = mock(AbsSender.class, this::telegram);
  private final EcsClient ecs = recordingMock(EcsClient.class, ecsCalls, Map.of(
      "listTasks", invocation -> ListTasksResponse.builder().build(),
      "describeTasks", invocation -> DescribeTasksResponse.builder().build()));
  private final Ec2Client ec2 = recordingMock(Ec2Client.class, ec2Calls, Map.of(
      "describeNetworkInterfaces", invocation -> DescribeNetworkInterfacesResponse.builder().build()));
  private final SecretsManagerClient secrets = recordingMock(SecretsManagerClient.class,
                                                             new CopyOnWriteArrayList<>(),
                                                             Map.of("getSecretValue", this::secret));

  @BeforeEach
  void setUp() {
    UserRecord root = new UserRecord();
    root.setUserId(String.valueOf(ROOT.id()));
    root.setUsername(ROOT.username());
    root.setPermissions(Set.of(Permission.ROOT_ACCESS.name()));
    table(UserRecord.class).withPages(List.of(root));

    RegionRecord region = new RegionRecord();
    region.setRegionId(REGION.id());
    region.setEcsClusterName("vpn-cluster");
    region.setEcsTaskDefinitionArn("arn:aws:ecs:eu-central-1:123456789012:task-definition/vpn:1");
    region.setSubnetId("subnet-1");
    region.setSecurityGroupId("sg-1");
    table(RegionRecord.class).withPages(List.of(region)).withItem(region);
  }

  @Test
  @DisplayName("AC-9, §4.7 rules 4 and 7: every accessor returns an object, the same one on every call")
  void everyAccessorReturnsASingleton() {
    VpnBot bot = bot(true, false);

    Map<String, Supplier<Object>> accessors = new LinkedHashMap<>();
    accessors.put("router", bot::router);
    accessors.put("systemCommands", bot::systemCommands);
    accessors.put("updateEvents", bot::updateEvents);
    accessors.put("messenger", bot::messenger);
    accessors.put("metrics", bot::metrics);
    accessors.put("pendingUserActivator", bot::pendingUserActivator);
    accessors.put("requestAuthenticator", bot::requestAuthenticator);

    assertAll(accessors.entrySet().stream().map(accessor -> (Executable) () -> {
      Object first = accessor.getValue().get();
      assertNotNull(first, accessor.getKey());
      assertSame(first, accessor.getValue().get(), accessor.getKey() + " is a singleton");
    }));
  }

  @Test
  @DisplayName("§4.7 rules 2 and 4: the tables come from the mocked client with the table name from Settings; building the graph sends no request to DynamoDB, ECS, EC2 or Telegram")
  void constructionSendsNoRequest() {
    bot(true, false);

    assertAll(
        () -> assertFalse(tableNames.isEmpty(), "no table taken from the enhanced client"),
        () -> assertTrue(tableNames.stream().allMatch(TABLE_NAME::equals), tableNames.toString()),
        () -> tables.forEach((beanClass, table) -> assertEquals(
            List.of(), table.timedCalls(), "DynamoDB calls on " + beanClass.getSimpleName())),
        () -> assertEquals(List.of(), ecsCalls, "ECS calls"),
        () -> assertEquals(List.of(), ec2Calls, "EC2 calls"),
        () -> assertEquals(List.of(), telegramCalls, "Telegram calls"));
  }

  @Test
  @DisplayName("§4.1, §4.7: VpnBot registers /help, /version, /supportedRegions and /describeRoles; root sees all four in this order, a user without permissions only /help")
  void systemCommandsRegistered() {
    VpnBot bot = bot(true, false);

    assertEquals(SYSTEM_COMMANDS, commandNames(bot.systemCommands().supportedCommands(ROOT)));
    assertEquals(List.of("/help"), commandNames(bot.systemCommands().supportedCommands(STRANGER)));
  }

  @Test
  @DisplayName("§4.7: a system command runs through the real authorizer and messenger: /version answers root's chat via the sender made from the bot token, with the version from Settings")
  void versionCommandAnswered() {
    VpnBot bot = bot(true, false);

    bot.systemCommands().handle(ROOT_CONTEXT, "/version");

    List<SendMessage> replies = sentMessages();
    assertEquals(1, replies.size(), telegramCalls.toString());
    assertEquals(String.valueOf(ROOT_CONTEXT.chatId()), replies.getFirst().getChatId());
    assertTrue(replies.getFirst().getText().contains(APP_VERSION), replies.getFirst().getText());
    assertEquals(List.of(BOT_TOKEN), senderTokens, "one sender, made from the resolved bot token");
  }

  @Test
  @DisplayName("AC-9, §4.7 rule 7: prime() runs without throwing and every priming step reaches its mock (DynamoDB, ECS and EC2 of the configured region, Telegram getMe), so nothing goes elsewhere")
  void primeUsesOnlyTheMocks() {
    VpnBot bot = bot(true, false);

    assertDoesNotThrow(bot::prime);

    List<TgUpdateLock> locks = table(TgUpdateLock.class).puts().stream()
        .map(put -> put.item())
        .toList();
    assertAll(
        () -> assertFalse(table(UserRecord.class).queries().isEmpty(), "permissions not primed"),
        () -> assertFalse(table(RegionRecord.class).queries().isEmpty(), "regions not primed"),
        () -> assertEquals(1, locks.size(), "update lock not primed"),
        () -> assertEquals("-1", locks.getFirst().getUpdateId()),
        () -> assertTrue(ecsRegions.contains(REGION), "ECS client of " + REGION + ": " + ecsRegions),
        () -> assertTrue(ecsCalls.containsAll(List.of("listTasks", "describeTasks")), ecsCalls.toString()),
        () -> assertTrue(ec2Regions.contains(REGION), "EC2 client of " + REGION + ": " + ec2Regions),
        () -> assertTrue(ec2Calls.contains("describeNetworkInterfaces"), ec2Calls.toString()),
        () -> assertTrue(telegramCalls.stream().anyMatch(GetMe.class::isInstance), telegramCalls.toString()),
        () -> assertEquals(List.of(BOT_TOKEN), senderTokens, "one sender, made from the resolved bot token"),
        () -> assertTrue(secretIds.contains(BOT_TOKEN_SECRET_ID), secretIds.toString()));
  }

  @Test
  @DisplayName("3 AC-A12 (moved from PermissionsServiceConfigurerTest): with the cache flag on the authorizer's permissions are cached – two /version requests, one USER query")
  void permissionsCachedWhenEnabled() {
    VpnBot bot = bot(true, true);
    FakeTable<UserRecord> users = table(UserRecord.class);
    int before = users.queries().size();

    bot.systemCommands().handle(ROOT_CONTEXT, "/version");
    bot.systemCommands().handle(ROOT_CONTEXT, "/version");

    assertEquals(1, users.queries().size() - before);
    assertVersionAnsweredTwice();
  }

  @Test
  @DisplayName("3 AC-A12 (moved from PermissionsServiceConfigurerTest): with the cache flag off every permission check queries DynamoDB – two /version requests, two USER queries")
  void permissionsNotCachedWhenDisabled() {
    VpnBot bot = bot(true, false);
    FakeTable<UserRecord> users = table(UserRecord.class);
    int before = users.queries().size();

    bot.systemCommands().handle(ROOT_CONTEXT, "/version");
    bot.systemCommands().handle(ROOT_CONTEXT, "/version");

    assertEquals(2, users.queries().size() - before);
    assertVersionAnsweredTwice();
  }

  @Test
  @DisplayName("2a §4.5: with METRICS_ENABLED on, metrics() is an EmfRequestMetrics that writes one EMF line to stdout per request")
  void metricsEnabled() {
    List<String> lines = emfLines(true);

    assertEquals(1, lines.size(), lines.toString());
    assertTrue(lines.getFirst().contains("\"UpdateKind\":\"Command\""), lines.getFirst());
  }

  @Test
  @DisplayName("2a §4.5: with METRICS_ENABLED off, metrics() is an EmfRequestMetrics that writes nothing")
  void metricsDisabled() {
    assertEquals(List.of(), emfLines(false));
  }

  @Test
  @DisplayName("§4.7 rule 2: the request authenticator checks the webhook header against the secret named by Settings.secretTokenId, read through the mocked Secrets Manager")
  void requestAuthenticatorUsesSecretTokenId() {
    RequestAuthenticator authenticator = bot(true, false).requestAuthenticator();

    assertDoesNotThrow(() -> authenticator.authenticate(webhookRequest(WEBHOOK_SECRET)));
    assertThrows(UnauthenticatedRequestException.class,
                 () -> authenticator.authenticate(webhookRequest("wrong")));
    assertTrue(secretIds.contains(SECRET_TOKEN_ID), secretIds.toString());
  }

  // ---- the graph and its externals

  private VpnBot bot(boolean metricsEnabled, boolean configCacheEnabled) {
    return new VpnBot(externals(settings(metricsEnabled, configCacheEnabled)));
  }

  /** The one place that knows the components of {@link Externals}. */
  private Externals externals(Settings settings) {
    return new Externals(dynamoDb(), secrets, this::sender, Clock.fixed(NOW, ZoneOffset.UTC),
                         settings, this::ecsClient, this::ec2Client);
  }

  /** The env values are fixed here; the yaml sections are those of the real application.yml. */
  private static Settings settings(boolean metricsEnabled, boolean configCacheEnabled) {
    YamlApplicationConfiguration yaml = MainApplicationYaml.load();
    return new Settings(TABLE_NAME, BOT_TOKEN_SECRET_ID, BOT_USERNAME, SECRET_TOKEN_ID,
                        metricsEnabled, configCacheEnabled, Duration.ofMinutes(5), APP_VERSION,
                        yaml.getAuth(), yaml.getEcs(), yaml.getAws());
  }

  private DynamoDbEnhancedClient dynamoDb() {
    DynamoDbEnhancedClient client = mock(DynamoDbEnhancedClient.class);
    when(client.table(anyString(), any())).thenAnswer(invocation -> {
      tableNames.add(invocation.getArgument(0));
      TableSchema<?> schema = invocation.getArgument(1);
      return table(schema.itemType().rawClass()).table();
    });
    return client;
  }

  /** One fake per bean class: every typed view of the config table shares its records. */
  @SuppressWarnings("unchecked")
  private <T> FakeTable<T> table(Class<T> beanClass) {
    return (FakeTable<T>) tables.computeIfAbsent(beanClass, type -> FakeTable.of(beanClass, () -> false));
  }

  private AbsSender sender(String botToken) {
    senderTokens.add(botToken);
    return sender;
  }

  private EcsClient ecsClient(Region region) {
    ecsRegions.add(region);
    return ecs;
  }

  private Ec2Client ec2Client(Region region) {
    ec2Regions.add(region);
    return ec2;
  }

  private Object secret(InvocationOnMock invocation) {
    String secretId = secretId(invocation.getArgument(0));
    secretIds.add(secretId);
    String value = switch (secretId) {
      case BOT_TOKEN_SECRET_ID -> BOT_TOKEN;
      case SECRET_TOKEN_ID -> WEBHOOK_SECRET;
      default -> throw new IllegalArgumentException("Unknown secret " + secretId);
    };
    return GetSecretValueResponse.builder().secretString(value).build();
  }

  @SuppressWarnings("unchecked")
  private static String secretId(Object request) {
    if (request instanceof GetSecretValueRequest getRequest) {
      return getRequest.secretId();
    }
    GetSecretValueRequest.Builder builder = GetSecretValueRequest.builder();
    ((Consumer<GetSecretValueRequest.Builder>) request).accept(builder);
    return builder.build().secretId();
  }

  /** {@code execute}: records the method; {@code getMe} gets the bot user, a sent message an id. */
  private Object telegram(InvocationOnMock invocation) throws Throwable {
    if (!invocation.getMethod().getName().equals("execute")
        || !(invocation.getArgument(0) instanceof BotApiMethod<?> method)) {
      return RETURNS_DEFAULTS.answer(invocation);
    }
    telegramCalls.add(method);
    if (method instanceof GetMe) {
      User me = new User();
      me.setId(1L);
      me.setUserName(BOT_USERNAME);
      me.setIsBot(true);
      return me;
    }
    if (method instanceof SendMessage) {
      Message message = new Message();
      message.setMessageId(telegramCalls.size());
      return message;
    }
    return null;
  }

  /**
   * A mock of an SDK client that records the name of every client method called and answers the
   * named ones; any other method gets Mockito's default (null, empty).
   */
  private static <T> T recordingMock(Class<T> type, List<String> calls,
                                     Map<String, Answer<?>> answers) {
    return mock(type, invocation -> {
      String name = invocation.getMethod().getName();
      if (invocation.getMethod().getDeclaringClass() == Object.class) {
        return RETURNS_DEFAULTS.answer(invocation);
      }
      calls.add(name);
      Answer<?> answer = answers.get(name);
      return answer == null ? RETURNS_DEFAULTS.answer(invocation) : answer.answer(invocation);
    });
  }

  // ---- observations

  private List<SendMessage> sentMessages() {
    return telegramCalls.stream()
        .filter(SendMessage.class::isInstance)
        .map(SendMessage.class::cast)
        .toList();
  }

  private void assertVersionAnsweredTwice() {
    List<SendMessage> replies = sentMessages();
    assertEquals(2, replies.size(), telegramCalls.toString());
    replies.forEach(reply -> assertTrue(reply.getText().contains(APP_VERSION), reply.getText()));
  }

  /** The command names of a /help list ("  /name – description" per line). */
  private static List<String> commandNames(String supportedCommands) {
    return supportedCommands.lines()
        .map(line -> line.strip().split("\\s+")[0])
        .toList();
  }

  /**
   * Builds the graph and runs one request's metrics with stdout captured; returns the EMF lines
   * (log lines may go to stdout too, so only lines with the {@code _aws} key count).
   */
  private List<String> emfLines(boolean metricsEnabled) {
    // log4j binds its console appender to stdout once: set it up before stdout is replaced
    LogManager.getLogger(VpnBotTest.class).debug("Capturing stdout for the metrics line");
    PrintStream stdout = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true, UTF_8));
    try {
      VpnBot bot = bot(metricsEnabled, false);
      assertInstanceOf(EmfRequestMetrics.class, bot.metrics());
      bot.metrics().start("Command");
      bot.metrics().finish();
    } finally {
      System.setOut(stdout);
    }
    return captured.toString(UTF_8).lines()
        .filter(line -> line.contains("\"_aws\""))
        .toList();
  }

  private static APIGatewayProxyRequestEvent webhookRequest(String secretHeader) {
    return new APIGatewayProxyRequestEvent()
        .withHeaders(Map.of("x-telegram-bot-api-secret-token", secretHeader));
  }

}

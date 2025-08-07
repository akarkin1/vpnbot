package org.github.akarkin1.it.init;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.concurrent.ThreadLocalRandom;

public class WireMockInitializer implements TestContainerInitializer<WireMockContainer> {

  public static final String TG_BOT_TOKEN = "0123456789:ABC-DEF1234ghIkl-zyx57W2v1u123ew11";
  public static final String TG_TEST_CHAT_ID = "111";
  public static final int WIRE_MOCK_PORT = 8080;

  @Override
  public void initialize(WireMockContainer container) {
    mockSendMessageEndpoint(container);
    populateAdditionalProperties(container);
  }

  private void mockSendMessageEndpoint(WireMockContainer container) {
    WireMock.configureFor(container.getHost(), container.getMappedPort(WIRE_MOCK_PORT));
    int messageId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    WireMock.stubFor(WireMock.post(WireMock.urlMatching("/bot%s/sendmessage".formatted(TG_BOT_TOKEN)))
      .willReturn(WireMock.aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"ok\":true,\"result\":{\"message_id\":%d,\"chat\":{\"id\":%s}}}".formatted(messageId, TG_TEST_CHAT_ID))));
  }

  private void populateAdditionalProperties(WireMockContainer container) {
    System.setProperty("tg.bot.token", TG_BOT_TOKEN);
    String tgBotBaseUrl = container.getUrl("bot%s/".formatted(TG_BOT_TOKEN));
    System.setProperty("tg.bot.api.base.url", tgBotBaseUrl);
  }

}

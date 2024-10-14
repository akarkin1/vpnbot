package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ec2.Ec2Manager;
import org.github.akarkin1.ec2.InstanceInfo;

import java.util.List;

@RequiredArgsConstructor
public final class ListInstancesCommand implements BotCommand<TextCommandResponse> {

  private static final String IP_V4_REGEX = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";

  private final Ec2Manager instanceManager;

  @Override
  public TextCommandResponse run(List<String> args) {
    val responseContent = new StringBuilder();

    responseContent
        .append("The lists of the servers available:")
        .append(System.lineSeparator());

    for (InstanceInfo instance : instanceManager.getAllInstances()) {
      responseContent
          .append("* %s".formatted(instance.getName()))
          .append(System.lineSeparator())
          .append(" - InstanceId: %s;".formatted(instance.getId()))
          .append(System.lineSeparator())
          .append(" - State: %s;".formatted(instance.getState()))
          .append(System.lineSeparator())
          .append(" - Location: %s (%s);".formatted(instance.getLocation(), instance.getRegion()));

      if (isIpV4(instance.getPublicIp())) {
        responseContent
            .append(System.lineSeparator())
            .append(" - Public IP: %s;".formatted(instance.getPublicIp()));
      }
      responseContent
          .append(System.lineSeparator())
          .append(System.lineSeparator());
    }

    return new TextCommandResponse(responseContent.toString());
  }

  @Override
  public String getDescription() {
    return "returns list of VPN Servers";
  }

  private static boolean isIpV4(String publicIp) {
    return StringUtils.isNotBlank(publicIp) && publicIp
        .matches(IP_V4_REGEX);
  }

}

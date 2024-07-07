package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ec2.Ec2InstanceManager;
import org.github.akarkin1.ec2.InstanceInfo;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

import java.util.List;

@RequiredArgsConstructor
public final class ListInstancesCommand implements BotCommand<TextCommandResponse> {

  private static final String IP_V4_REGEX = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";

  private final Ec2ClientProvider clientProvider;

  @Override
  public TextCommandResponse run(List<String> args) {
    val responseContent = new StringBuilder();
    for (Region region : this.getSupportedRegions()) {
      Ec2Client client = clientProvider.getForRegion(region.id());
      Ec2InstanceManager instanceManager = new Ec2InstanceManager(client);
      List<InstanceInfo> instances = instanceManager.getInstances();
      for (int i = 0; i < instances.size(); i++) {
        InstanceInfo instance = instances.get(i);
        responseContent
            .append("%d. %s".formatted(i + 1, instance.getName()))
            .append(System.lineSeparator())
            .append(" - InstanceId: %s;".formatted(instance.getId()))
            .append(System.lineSeparator())
            .append(" - State: %s;".formatted(instance.getState()))
            .append(System.lineSeparator())
            .append(" - Region: %s (%s);".formatted(region.id(), instance.getLocation()));

        if (isIpV4(instance.getPublicIp())) {
          responseContent
              .append(System.lineSeparator())
              .append(" - Public IP: %s;".formatted(instance.getPublicIp()));
        }
        responseContent
              .append(System.lineSeparator());
      }
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

  private List<Region> getSupportedRegions() {
   return List.of(Region.US_EAST_1, Region.EU_WEST_2, Region.EU_NORTH_1);
  }

}

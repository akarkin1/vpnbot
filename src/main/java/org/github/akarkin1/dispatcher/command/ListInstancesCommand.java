package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ec2.Ec2InstanceManager;
import org.github.akarkin1.ec2.InstanceInfo;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Region;

import java.util.List;

@RequiredArgsConstructor
public final class ListInstancesCommand implements BotCommand<TextCommandResponse> {

  private static final String IP_V4_REGEX = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";

  private final Ec2ClientProvider clientProvider;

  @Override
  public TextCommandResponse run(List<String> args) {
    val responseContent = new StringBuilder();
    for (Region region : this.getAllRegions()) {
      Ec2Client client = clientProvider.getForRegion(region.regionName());
      Ec2InstanceManager instanceManager = new Ec2InstanceManager(client);
      List<InstanceInfo> instances = instanceManager.getInstances();
      for (int i = 0; i < instances.size(); i++) {
        int pointNumber = i + 1;
        InstanceInfo instance = instances.get(i);
        responseContent
            .append("%d. %s".formatted(pointNumber, instance.getName()))
            .append(System.lineSeparator())
            .append(" - InstanceId: %s;".formatted(instance.getId()))
            .append(System.lineSeparator())
            .append(" - State: %s;".formatted(instance.getState()))
            .append(System.lineSeparator())
            .append(" - Region: %s (%s);".formatted(region.regionName(), instance.getLocation()));

        if (isIpV4(instance.getPublicIp())) {
          responseContent
              .append(System.lineSeparator())
              .append(instance.getPublicIp())
              .append(System.lineSeparator());
        }
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

  private List<Region> getAllRegions() {
    Ec2Client ec2Client = clientProvider.getForGlobal();
    Ec2InstanceManager instanceManager = new Ec2InstanceManager(ec2Client);
    return instanceManager.getAllRegions();
  }

}

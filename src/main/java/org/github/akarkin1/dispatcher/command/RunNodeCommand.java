package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.UserAction;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgUserContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public final class RunNodeCommand implements BotCommand<EmptyResponse> {

  private final TailscaleNodeService tailscaleNodeService;
  private final Authorizer authorizer;
  private final Consumer<String> messageConsumer;

  @Override
  public EmptyResponse run(List<String> args) {
    if (!authorizer.isAllowed(TgUserContext.getUsername(), UserAction.RUN_NODES)) {
      messageConsumer.accept("You are not authorized to execute this command. "
                             + "Please, reach out @karkin_ai to request an access.");
      return EmptyResponse.NONE;
    }

    if (args.isEmpty()) {
      messageConsumer.accept("Missing Region arguments. Command description: " + getDescription());
      return EmptyResponse.NONE;
    }

    String userRegion = args.getFirst();

    if (!tailscaleNodeService.isRegionValid(userRegion)) {
      messageConsumer.accept(
          "Invalid region name: provided region is not valid region in AWS: %s.".formatted(
              userRegion));
      return EmptyResponse.NONE;
    }

    if (!tailscaleNodeService.isRegionSupported(userRegion)) {
      messageConsumer.accept(
          ("Currently the region '%s' is not supported. You may ask @karkin_ai to set up "
           + "configuration for this region or just launch your node in some of the supported region. "
           + "To find out what are the supported regions, you may use /supportedRegions command.")
              .formatted(userRegion));
      return EmptyResponse.NONE;
    }

    String userHost = null;
    if (args.size() > 1) {
      userHost = args.get(1);
      if (!tailscaleNodeService.isHostnameAvailable(userRegion, userHost)) {
        messageConsumer.accept(
            "A node with such name is incorrect or already in use: %s.".formatted(userHost)
            + "Please, choose another node name or skip the parameter, so the "
            + "name will be chosen automatically.");
        return EmptyResponse.NONE;
      }
    }

    messageConsumer.accept("Running the node...");
    TaskInfo taskInfo = tailscaleNodeService.runNode(userRegion,
                                                     TgUserContext.getUsername(),
                                                     userHost);
    log.debug("Task is run, task info: {}", taskInfo);
    messageConsumer.accept("Task is started. Checking Tailscale node status...");
    RunTaskStatus runTaskStatus = tailscaleNodeService.checkNodeStatus(taskInfo);
    if (RunTaskStatus.UNKNOWN.equals(runTaskStatus)) {
      messageConsumer.accept("Failed to check task status. Task has been starting for too long. "
                             + "Please check its status later with /listRunningNodes command.");
    } else if (RunTaskStatus.UNHEALTHY.equals(runTaskStatus)) {
      messageConsumer.accept("The node failed to start. Please, reach out @karkin_ai to "
                             + "troubleshoot the issue.");
    } else {
      Optional<TaskInfo> fullTaskInfo = tailscaleNodeService.getFullTaskInfo(taskInfo.getRegion(),
                                                                             taskInfo.getCluster(),
                                                                             taskInfo.getId());
      StringBuilder successMessage = new StringBuilder("The node has been started successfully.");
      fullTaskInfo.ifPresent(fullInfoLocal -> successMessage.append(" Node details:\n")
          .append("\t- Node Name: %s%n".formatted(fullInfoLocal.getHostName()))
          .append("\t\tNode Status: %s%n".formatted(fullInfoLocal.getState()))
          .append("\t\tPublic IP: %s%n".formatted(fullInfoLocal.getPublicIp()))
          .append("\t\tLocation: %s (%s)".formatted(fullInfoLocal.getLocation(),
                                                    fullInfoLocal.getRegion().id())));

      messageConsumer.accept(successMessage.toString());
    }

    return EmptyResponse.NONE;
  }

  @Override
  public String getDescription() {
    return """
        Runs Tailscale VPN node in specified region. 
        USAGE: /runNode RegionName [NodeName], 
        whereas 
         - RegionName – is either AWS Region Id or Name of the city, where the node will be run;
         - NodeName (optional) – hostname of Tailscale node (should be unique within all running nodes)
        """;
  }
}

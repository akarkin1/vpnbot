package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.message.MessageConsumer;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.service.ServiceType;
import org.github.akarkin1.tg.TgRequestContext;

import java.util.List;
import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
public final class RunNodeCommand implements BotCommand<EmptyResponse> {

  private final NodeService nodeService;
  private final MessageConsumer messageConsumer;

  @Override
  public EmptyResponse run(List<String> args) {
    if (args.size() < 2) {
      messageConsumer.accept(
        "${command.run-node.missing-arg.error}. ${common.command.description.message}: "
        + getDescription());
      return EmptyResponse.NONE;
    }

    // First argument is the service type
    String serviceTypeStr = args.get(0);
    ServiceType serviceType;
    try {
      serviceType = ServiceType.valueOf(serviceTypeStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      messageConsumer.accept("${command.run-node.invalid-service-type.error}", serviceTypeStr);
      return EmptyResponse.NONE;
    }

    // Second argument is the region
    String userRegion = args.get(1);

    if (!nodeService.isRegionValid(userRegion)) {
      messageConsumer.accept(
        "${common.region.invalid-name.error}",
        userRegion);
      return EmptyResponse.NONE;
    }

    if (!nodeService.isRegionSupported(userRegion)) {
      messageConsumer.accept("${common.region.not-supported.error}",
                             userRegion);
      return EmptyResponse.NONE;
    }

    // Optional third argument is the hostname
    String userHost = null;
    if (args.size() > 2) {
      userHost = args.get(2);
      if (!nodeService.isHostnameAvailable(userRegion, userHost)) {
        messageConsumer.accept(
          "${command.run-node.node.name-is-incorrect-or-in-use.error}",
          userHost);
        return EmptyResponse.NONE;
      }
    }

    messageConsumer.accept("${command.run-node.node.running.message}", serviceType.getDisplayName());
    TaskInfo taskInfo = nodeService.runNode(userRegion,
                                           TgRequestContext.getUsername(),
                                           userHost,
                                           serviceType);
    log.debug("Task is run, task info: {}", taskInfo);
    messageConsumer.accept("${command.run-node.task.started.message}");
    RunTaskStatus runTaskStatus = nodeService.checkNodeStatus(taskInfo);
    if (RunTaskStatus.UNKNOWN.equals(runTaskStatus)) {
      messageConsumer.accept("${command.run-node.status.check-failed.error}");
    } else if (RunTaskStatus.UNHEALTHY.equals(runTaskStatus)) {
      messageConsumer.accept("${command.run-node.node.start-failed.error}");
    } else {
      Optional<TaskInfo> fullTaskInfo = nodeService.getFullTaskInfo(taskInfo.getRegion(),
                                                                   taskInfo.getCluster(),
                                                                   taskInfo.getId());
      StringBuilder successMessage = new StringBuilder("${command.run-node.node.start-succeed.message}");
      fullTaskInfo.ifPresent(fullInfoLocal -> successMessage.append(" ${common.node.details.message}:\n")
        .append("\t- ${common.node.name.message}: %s%n".formatted(fullInfoLocal.getHostName()))
        .append("\t\t${common.node.type.message}: %s%n".formatted(fullInfoLocal.getServiceType().getDisplayName()))
        .append("\t\t${common.node.status.message}: %s%n".formatted(fullInfoLocal.getState()))
        .append("\t\t${common.node.public-ip.message}: %s%n".formatted(fullInfoLocal.getPublicIp()))
        .append("\t\t${common.node.location.message}: %s (%s)".formatted(fullInfoLocal.getLocation(),
                                                  fullInfoLocal.getRegion().id())));

      messageConsumer.accept(successMessage.toString());
    }

    return EmptyResponse.NONE;
  }

  @Override
  public String getDescription() {
    return "${command.run-node.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.RUN_NODES);
  }

}

package org.github.akarkin1.dispatcher.command;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import org.github.akarkin1.message.MessageConsumer;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.tg.TgRequestContext;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Log4j2
public final class RunNodeCommand implements BotCommand<EmptyResponse> {

  private final NodeService nodeService;
  private final Authorizer authorizer;
  private final MessageConsumer messageConsumer;

  public RunNodeCommand(NodeService nodeService, Authorizer authorizer, MessageConsumer messageConsumer) {
    this.nodeService = nodeService;
    this.authorizer = authorizer;
    this.messageConsumer = messageConsumer;
  }

  @Override
  public EmptyResponse run(List<String> args) {
    ServiceRegion params = parseArguments(args);

    if (params.isBlank()) {
      messageConsumer.accept(
          "${command.run-node.missing-arg.error}. ${common.command.description.message}: "
              + getDescription());
      return EmptyResponse.NONE;
    }

    String serviceName = params.service();
    String userRegion = params.regionName();

    if (!nodeService.isRegionValid(userRegion)) {
      messageConsumer.accept(
          "${common.region.invalid-name.error}",
          userRegion);
      return EmptyResponse.NONE;
    }

    if (!nodeService.isRegionSupported(userRegion, serviceName)) {
      messageConsumer.accept("${common.region.not-supported.error}",
          userRegion);
      return EmptyResponse.NONE;
    }

    if (StringUtils.isBlank(serviceName)) {
      messageConsumer.accept("${command.run-node.missing-service-arg.error}. ${common.command.description.message}: "
          + getDescription());
      return EmptyResponse.NONE;
    }

    if (!authorizer.hasPermissionForService(TgRequestContext.getUsername(), Permission.RUN_NODES, serviceName)) {
      messageConsumer.accept("${command.run-node.not-authorized-to-run-service.error}", serviceName);
      return EmptyResponse.NONE;
    }

    messageConsumer.accept("${command.run-node.node.running.message}", serviceName);
    List<String> optionalArguments = args.size() > 2 ? args.subList(2, args.size()) : Collections.emptyList();
    TaskInfo taskInfo = nodeService.runNode(userRegion,
        TgRequestContext.getUsername(),
        serviceName,
        optionalArguments);
    log.debug("Task was run, task info: {}", taskInfo);
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
      StringBuilder successMessage = new StringBuilder(
          "${command.run-node.node.start-succeed.message}");
      fullTaskInfo.ifPresent(
          fullInfoLocal -> successMessage.append(" ${common.node.details.message}:\n")
              .append("\t- ${common.node.name.message}: %s%n".formatted(fullInfoLocal.getHostName()))
              .append("\t\t${common.node.type.message}: %s%n".formatted(fullInfoLocal.getServiceName()))
              .append("\t\t${common.node.status.message}: %s%n".formatted(fullInfoLocal.getState()))
              .append(
                  "\t\t${common.node.public-ip.message}: %s%n".formatted(fullInfoLocal.getPublicIp()))
              .append(
                  "\t\t${common.node.location.message}: %s (%s)".formatted(fullInfoLocal.getLocation(),
                      fullInfoLocal.getRegion()
                          .id())));

      messageConsumer.accept(successMessage.toString());
    }

    return EmptyResponse.NONE;
  }

  private ServiceRegion parseArguments(List<String> args) {
    String username = TgRequestContext.getUsername();

    Set<String> allowedServices = authorizer.getAllowedServices(username, Permission.RUN_NODES);

    if (allowedServices.isEmpty()) {
      throw new CommandExecutionFailedException("User '%s' is not authorized to run any service".formatted(username));
    }

    if (args.size() == 1 && allowedServices.size() == 1) {
      return new ServiceRegion(allowedServices.iterator().next(), args.getFirst());
    } else if (args.size() > 1) {
      return new ServiceRegion(args.get(0), args.get(1));
    }

    return new ServiceRegion(null, null);
  }

  @Override
  public String getDescription() {
    return "${command.run-node.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.RUN_NODES);
  }

  private record ServiceRegion(String service, String regionName) {
    boolean isBlank() {
      return StringUtils.isBlank(service) && StringUtils.isBlank(regionName);
    }
  }

}

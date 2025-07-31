package org.github.akarkin1.service;

import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Optional;

/**
 * Interface for managing nodes of different service types.
 */
public interface NodeService {

  /**
   * Check if the region is valid.
   *
   * @param userRegion the region to check
   * @return true if the region is valid, false otherwise
   */
  boolean isRegionValid(String userRegion);

  /**
   * Check if the region is supported.
   *
   * @param userRegion the region to check
   * @return true if the region is supported, false otherwise
   */
  boolean isRegionSupported(String userRegion);

  /**
   * Check if the hostname is available.
   *
   * @param userRegion the region to check
   * @param userHostName the hostname to check
   * @return true if the hostname is available, false otherwise
   */
  boolean isHostnameAvailable(String userRegion, String userHostName);

  /**
   * Run a node.
   *
   * @param userRegion the region to run the node in
   * @param userTgId the Telegram user ID
   * @param userHostName the hostname for the node
   * @param serviceType the type of service to run
   * @return information about the task
   */
  TaskInfo runNode(String userRegion, String userTgId, String userHostName, ServiceType serviceType);

  /**
   * Get full information about a task.
   *
   * @param region the region the task is running in
   * @param clusterName the cluster the task is running in
   * @param taskId the ID of the task
   * @return information about the task, or empty if not found
   */
  Optional<TaskInfo> getFullTaskInfo(Region region, String clusterName, String taskId);

  /**
   * Check the status of a node.
   *
   * @param taskInfo information about the task
   * @return the status of the task
   */
  RunTaskStatus checkNodeStatus(TaskInfo taskInfo);

  /**
   * List tasks.
   *
   * @param userTgId the Telegram user ID, or null to list all tasks
   * @return a list of tasks
   */
  List<TaskInfo> listTasks(String userTgId);

  /**
   * Get descriptions of supported regions.
   *
   * @return a list of region descriptions
   */
  List<String> getSupportedRegionDescriptions();

  /**
   * Get the supported service types.
   *
   * @return a list of supported service types
   */
  List<ServiceType> getSupportedServiceTypes();
}
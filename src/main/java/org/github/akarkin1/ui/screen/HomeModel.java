package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ecs.TaskInfo;

import java.util.List;

/**
 * @param canManageUsers the user may open the Users screen ({@code USER_MANAGEMENT} or root)
 * @param hasAccess      the user has at least one permission; otherwise the home screen offers to
 *                       request access
 */
public record HomeModel(String firstName, String username, boolean canListNodes, boolean canRunNodes,
                        boolean allNodes, List<TaskInfo> nodes, List<String> regionIds,
                        List<String> stoppableTaskIds, boolean canManageUsers, boolean hasAccess) {

}

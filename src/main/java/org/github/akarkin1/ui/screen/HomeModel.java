package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ecs.TaskInfo;

import java.util.List;

public record HomeModel(String firstName, String username, boolean canListNodes, boolean canRunNodes,
                        boolean allNodes, List<TaskInfo> nodes, List<String> regionIds,
                        List<String> stoppableTaskIds) {

}

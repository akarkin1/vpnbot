package org.github.akarkin1.vpn;

import org.github.akarkin1.ecs.ServerInstance;

import java.util.List;

public interface VpnManager {

  ServerInstance runVpnServer(String region);

  List<ServerInstance> getRunningInstances();

}

package org.github.akarkin1.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing different types of services that can be run in ECS.
 */
@Getter
@RequiredArgsConstructor
public enum ServiceType {
    VPN("tailscale-node", "VPN"),
    MINECRAFT("minecraft-server", "Minecraft Server");

    private final String serviceName;
    private final String displayName;

    /**
     * Get the ServiceType from a service name.
     *
     * @param serviceName the service name
     * @return the ServiceType, or null if not found
     */
    public static ServiceType fromServiceName(String serviceName) {
        for (ServiceType type : values()) {
            if (type.getServiceName().equals(serviceName)) {
                return type;
            }
        }
        return null;
    }
}
package org.github.akarkin1.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
public class UserEntitlements {

    // key – username, value Entitlement – a user permission for specific service
    private Map<String, List<Entitlement>> userEntitlements;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entitlement {
        private String service;
        private Permission permission;

        @Override
        public String toString() {
            return service == null ? permission.name() : "%s:%s".formatted(service, permission.name());
        }
    }
}

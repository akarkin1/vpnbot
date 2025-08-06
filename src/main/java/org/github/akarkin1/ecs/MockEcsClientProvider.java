package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

import javax.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MockEcsClientProvider implements EcsClientProvider {

    private final Map<Region, EcsClient> clientsByRegion = new ConcurrentHashMap<>();
    private final EcsClient defaultClient = new EcsClientMock();

    @Override
    public EcsClient get() {
        return defaultClient;
    }

    @Override
    public EcsClient get(Region region) {
        return clientsByRegion.computeIfAbsent(region, ignored -> new EcsClientMock(region.id()));
    }

}

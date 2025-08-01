package org.github.akarkin1.ecs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class EcsClientPoolTest {

    private final EcsClientPool pool = new EcsClientPool();

    @Test
    void testGet_shouldReturnEcsClient() {
        // When
        EcsClient client = pool.get();

        // Then
        assertNotNull(client);
        client.close();
    }

    @Test
    void testGet_shouldReturnSameClientOnMultipleCalls() {
        // When
        EcsClient client1 = pool.get();
        EcsClient client2 = pool.get();

        // Then
        assertSame(client1, client2);
        client1.close();
    }

    @Test
    void testGetWithRegion_shouldReturnEcsClient() {
        // Given
        Region region = Region.US_EAST_1;

        // When
        EcsClient client = pool.get(region);

        // Then
        assertNotNull(client);
        client.close();
    }

    @Test
    void testGetWithRegion_shouldReturnSameClientForSameRegion() {
        // Given
        Region region = Region.US_EAST_1;

        // When
        EcsClient client1 = pool.get(region);
        EcsClient client2 = pool.get(region);

        // Then
        assertSame(client1, client2);
        client1.close();
    }
}

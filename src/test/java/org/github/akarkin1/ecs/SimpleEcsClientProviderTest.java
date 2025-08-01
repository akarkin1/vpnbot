package org.github.akarkin1.ecs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class SimpleEcsClientProviderTest {

    private final SimpleEcsClientProvider provider = new SimpleEcsClientProvider();

    @Test
    void testGet_shouldReturnEcsClient() {
        // When
        EcsClient client = provider.get();

        // Then
        assertNotNull(client);
        client.close();
    }

    @Test
    void testGetWithRegion_shouldReturnEcsClient() {
        // Given
        Region region = Region.US_EAST_1;

        // When
        EcsClient client = provider.get(region);

        // Then
        assertNotNull(client);
        client.close();
    }
}

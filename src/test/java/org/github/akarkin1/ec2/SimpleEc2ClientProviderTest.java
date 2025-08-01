package org.github.akarkin1.ec2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ec2.Ec2Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class SimpleEc2ClientProviderTest {

    private final SimpleEc2ClientProvider provider = new SimpleEc2ClientProvider();

    @Test
    void testGetForRegion_shouldReturnEc2Client() {
        // Given
        String regionName = "us-east-1";

        // When
        Ec2Client client = provider.getForRegion(regionName);

        // Then
        assertNotNull(client);
        client.close();
    }

    @Test
    void testGetForGlobal_shouldReturnEc2Client() {
        // When
        Ec2Client client = provider.getForGlobal();

        // Then
        assertNotNull(client);
        client.close();
    }
}

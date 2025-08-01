package org.github.akarkin1.ec2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ec2.Ec2Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class Ec2ClientPoolTest {

    private final Ec2ClientPool pool = new Ec2ClientPool();

    @Test
    void testGetForRegion_shouldReturnEc2Client() {
        // Given
        String regionName = "us-east-1";

        // When
        Ec2Client client = pool.getForRegion(regionName);

        // Then
        assertNotNull(client);
        client.close();
    }

    @Test
    void testGetForRegion_shouldReturnSameClientForSameRegion() {
        // Given
        String regionName = "us-east-1";

        // When
        Ec2Client client1 = pool.getForRegion(regionName);
        Ec2Client client2 = pool.getForRegion(regionName);

        // Then
        assertSame(client1, client2);
        client1.close();
    }

    @Test
    void testGetForGlobal_shouldReturnEc2Client() {
        // When
        Ec2Client client = pool.getForGlobal();

        // Then
        assertNotNull(client);
        client.close();
    }

    @Test
    void testGetForGlobal_shouldReturnSameClientOnMultipleCalls() {
        // When
        Ec2Client client1 = pool.getForGlobal();
        Ec2Client client2 = pool.getForGlobal();

        // Then
        assertSame(client1, client2);
        client1.close();
    }
}

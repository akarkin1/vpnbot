package org.github.akarkin1.ec2;

import org.mockito.Mockito;
import software.amazon.awssdk.services.ec2.Ec2Client;

import javax.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MockEc2ClientProvider implements Ec2ClientProvider {

    private final Map<String, Ec2Client> clientsByRegion = new ConcurrentHashMap<>();
    private final Ec2Client globalClient = Mockito.mock(Ec2Client.class);

    @Override
    public Ec2Client getForRegion(String regionName) {
        return clientsByRegion.computeIfAbsent(regionName, r -> Mockito.mock(Ec2Client.class));
    }

    @Override
    public Ec2Client getForGlobal() {
        return globalClient;
    }

    public Ec2Client getGlobalClient() {
        return globalClient;
    }

    public Ec2Client getClientForRegion(String regionName) {
        return clientsByRegion.computeIfAbsent(regionName, r -> Mockito.mock(Ec2Client.class));
    }

    public Map<String, Ec2Client> getAllClients() {
        return new ConcurrentHashMap<>(clientsByRegion);
    }
}

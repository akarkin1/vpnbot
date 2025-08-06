package org.github.akarkin1.ec2;

import software.amazon.awssdk.services.ec2.Ec2Client;

import javax.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MockEc2ClientProvider implements Ec2ClientProvider {

    private final Map<String, Ec2Client> clientsByRegion = new ConcurrentHashMap<>();
    private final Ec2Client globalClient = new Ec2ClientMock();

    @Override
    public Ec2Client getForRegion(String regionName) {
        return clientsByRegion.computeIfAbsent(regionName, r -> new Ec2ClientMock(regionName));
    }

    @Override
    public Ec2Client getForGlobal() {
        return globalClient;
    }
}

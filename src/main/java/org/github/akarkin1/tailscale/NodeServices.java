package org.github.akarkin1.tailscale;

import org.github.akarkin1.ec2.Ec2ClientPool;

/**
 * What {@link TailscaleEcsNodeServiceConfigurer} builds: the node service and the EC2 client pool
 * it uses (the SnapStart primer needs the pool too).
 */
public record NodeServices(TailscaleNodeService nodeService, Ec2ClientPool ec2ClientPool) {

}

package brooklyn.demo

import brooklyn.entity.basic.AbstractApplication
import java.util.Map

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.WebAppRunner
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.location.basic.aws.AwsLocationFactory
import brooklyn.management.internal.AbstractManagementContext

import com.google.common.base.Preconditions

/**
 * The application demonstrates the following:
 * 
 * <ul>
 * <li>Dynamic clusters of web application servers
 * <li>Multiple geographic locations
 * <li>Use of any geo-redirecting DNS provider to route users to their closest cluster of web servers
 * <li>Resizing the clusters to meet client demand
 * </ul>
 */
public class SpringTravel extends AbstractApplication {
    final DynamicFabric fabric
    final DynamicGroup nginxEntities
    
    SpringTravel(Map props=[:]) {
        super(props)
        
        fabric = new DynamicFabric(
            [
                id : 'fabricID',
	            name : 'fabricName',
	            displayName : 'Fabric',
	            newEntity : { Map properties -> return new WebCluster(properties) }
            ],
            this)
        Preconditions.checkState fabric.displayName == "Fabric"

        nginxEntities = new DynamicGroup([:], this, { Entity e -> (e instanceof NginxController) })

        GeoscalingDnsService geoDns = new GeoscalingDnsService(
            config: [
                (GeoscalingDnsService.GEOSCALING_USERNAME): 'cloudsoft',
                (GeoscalingDnsService.GEOSCALING_PASSWORD): 'cl0uds0ft',
                (GeoscalingDnsService.GEOSCALING_PRIMARY_DOMAIN_NAME): 'geopaas.org',
                (GeoscalingDnsService.GEOSCALING_SMART_SUBDOMAIN_NAME): 'cloudsoft',
            ],
            this)

        nginxEntities.rescanEntities()
        geoDns.setGroup(nginxEntities)
    }
    
    @Override
    public void restart() {
        throw new UnsupportedOperationException()
    }
}

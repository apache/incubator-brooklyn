package com.cloudsoftcorp.monterey.brooklyn.example

import java.net.URL
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.policy.ResizerPolicy

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode
import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.control.plane.web.api.ControlPlaneWebConstants.HTTP_AUTH
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.javalang.OsgiClassLoadingContextFromBundle
import com.google.common.base.Preconditions

public class MontereySpringTravel extends AbstractApplication {
    final DynamicFabric webFabric
    final DynamicGroup nginxEntities
    final GeoscalingDnsService geoDns
 
    private static final String SPRING_TRAVEL_PATH = "src/main/resources/booking-mvc.war"
    private static final String APP_BUNDLE_RESOURCE_PATH = "com.cloudsoftcorp.sample.booking.svc.impl_3.2.0.v20110502-351-10779.jar"
    private static final String APP_CONFIG_RESOURCE_PATH = "BookingAvailabilityApplication.conf"
    
    MontereyNetwork mn
    
    public MontereySpringTravel(Map props=[:]) {
        super(props)

        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereySpringTravel.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);

        URL bundleUrl = MontereySpringTravel.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
        URL appDescriptorUrl = MontereySpringTravel.class.getClassLoader().getResource(APP_CONFIG_RESOURCE_PATH);
        UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);

        mn = new MontereyNetwork(owner:this)
        mn.name = "Spring Travel"
        mn.setConfig(MontereyNetwork.APP_BUNDLES, [bundleUrl])
        mn.setConfig(MontereyNetwork.APP_DESCRIPTOR_URL, appDescriptorUrl)
        mn.setConfig(MontereyManagementNode.SUGGESTED_WEB_USERS_CREDENTIAL, [adminCredential])
        mn.setConfig(MontereyNetwork.INITIAL_TOPOLOGY_PER_LOCATION, [(Dmn1NodeType.LPP):1,(Dmn1NodeType.MR):1,(Dmn1NodeType.M):1,(Dmn1NodeType.TP):1,(Dmn1NodeType.SPARE):1])
        //mn.policy << new MontereyLatencyOptimisationPolicy()
    
        webFabric = new DynamicFabric(
            [
                name : 'web-cluster-fabric',
                displayName : 'Fabric',
                displayNamePrefix : '',
                displayNameSuffix : ' web cluster',
                newEntity : { Map properties -> 
                    MontereyWebCluster webCluster = new MontereyWebCluster(properties, webFabric, mn)
                    
                    ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
                    policy.setMinSize(1)
                    policy.setMaxSize(5)
                    policy.setMetricLowerBound(10)
                    policy.setMetricUpperBound(100)
                    webCluster.cluster.addPolicy(policy)
 
                    return webCluster
                }
            ],
            this)
        webFabric.setConfig(JavaWebApp.WAR, SPRING_TRAVEL_PATH)
        
        Preconditions.checkState webFabric.displayName == "Fabric"

        nginxEntities = new DynamicGroup([displayName: 'Web Fronts'], this, { Entity e -> (e instanceof NginxController) })
        geoDns = new GeoscalingDnsService(displayName: 'Geo-DNS (brooklyn.geopaas.org)',
            username: 'cloudsoft', password: 'cl0uds0ft', primaryDomainName: 'geopaas.org', smartSubdomainName: 'brooklyn',
            this)
        geoDns.setTargetEntityProvider(nginxEntities)
    }
}
	
package com.cloudsoftcorp.monterey.brooklyn.example

import java.net.URL
import java.util.Map

import brooklyn.demo.SpringTravel
import brooklyn.management.internal.AbstractManagementContext

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode
import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.control.plane.web.api.ControlPlaneWebConstants.HTTP_AUTH
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.javalang.OsgiClassLoadingContextFromBundle

public class MontereySpringTravel extends SpringTravel {
    private static final String APP_BUNDLE_RESOURCE_PATH = "com.cloudsoftcorp.monterey.example.noapisimple.jar"
    private static final String APP_CONFIG_RESOURCE_PATH = "HelloCloud.conf"
    
    MontereyNetwork mn
    
    public MontereySpringTravel(Map props=[:]) {
        super(props)

        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereySpringTravel.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);

        URL bundleUrl = MontereySpringTravel.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
        URL appDescriptorUrl = MontereySpringTravel.class.getClassLoader().getResource(APP_CONFIG_RESOURCE_PATH);
        UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);

        mn = new MontereyNetwork(owner:this)
        mn.name = "HelloCloud"
        mn.setConfig(MontereyNetwork.APP_BUNDLES, [bundleUrl])
        mn.setConfig(MontereyNetwork.APP_DESCRIPTOR_URL, appDescriptorUrl)
        mn.setConfig(MontereyManagementNode.SUGGESTED_WEB_USERS_CREDENTIAL, [adminCredential])
        mn.setConfig(MontereyNetwork.INITIAL_TOPOLOGY_PER_LOCATION, [(Dmn1NodeType.LPP):1,(Dmn1NodeType.MR):1,(Dmn1NodeType.M):1,(Dmn1NodeType.TP):1,(Dmn1NodeType.SPARE):1])
        //mn.policy << new MontereyLatencyOptimisationPolicy()
    }
}

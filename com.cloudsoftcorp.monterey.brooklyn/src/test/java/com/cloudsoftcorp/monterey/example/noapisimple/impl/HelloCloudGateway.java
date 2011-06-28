package com.cloudsoftcorp.monterey.example.noapisimple.impl;

import com.cloudsoftcorp.monterey.network.api.LppClientGateway;
import com.cloudsoftcorp.monterey.network.api.LppClientGatewayFactory;
import com.cloudsoftcorp.monterey.network.api.LppStateBackup;
import com.cloudsoftcorp.monterey.servicebean.api.BeanLppClientGateway;

public class HelloCloudGateway extends BeanLppClientGateway {

    public static class Factory implements LppClientGatewayFactory {
        public LppClientGateway newClientGateway() {
            return new HelloCloudGateway();
        }
        public LppStateBackup newClientGatewayBackup() {
            return null; // Unsupported
        }
    }
    
    public HelloCloudGateway() {
        addBot(new HelloCloudBot());
        setSupportsProxyingLpp(true);
    }
}

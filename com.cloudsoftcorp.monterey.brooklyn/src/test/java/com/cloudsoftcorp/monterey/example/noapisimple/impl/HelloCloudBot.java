package com.cloudsoftcorp.monterey.example.noapisimple.impl;

import com.cloudsoftcorp.monterey.servicebean.access.api.MontereyNetworkEndpoint;
import com.cloudsoftcorp.monterey.servicebean.api.ServiceBeanBot;

import com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceLocatorImpl;
import com.cloudsoftcorp.monterey.example.noapisimple.Helloee;

public class HelloCloudBot implements ServiceBeanBot {

    private static final int SERVICE_CALL_TIMEOUT_MILLIS = 30*1000;
    
    private HelloCloudServiceLocatorImpl locator;
    
    public void init(MontereyNetworkEndpoint endpoint) {
        locator = new HelloCloudServiceLocatorImpl(endpoint);
        locator.setServiceCallsTimeoutInMillis(SERVICE_CALL_TIMEOUT_MILLIS);
    }

    public void shutdown() {
        // no-op
    }
    
    public void doRequest(String segment) {
        Helloee service = locator.getService(segment);
        // TODO: implement request logic here
    }
}

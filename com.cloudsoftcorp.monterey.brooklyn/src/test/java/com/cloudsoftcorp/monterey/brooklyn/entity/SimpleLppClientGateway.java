package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.io.Serializable;

import com.cloudsoftcorp.monterey.network.api.ClientGatewayContext;
import com.cloudsoftcorp.monterey.network.api.LppClientGateway;
import com.cloudsoftcorp.monterey.network.api.LppClientGatewayFactory;
import com.cloudsoftcorp.monterey.network.api.LppStateBackup;

public class SimpleLppClientGateway implements LppClientGateway {

    public static class Factory implements LppClientGatewayFactory {
        public LppClientGateway newClientGateway() {
            return new SimpleLppClientGateway();
        }
        public LppStateBackup newClientGatewayBackup() {
            return null; // Unsupported
        }
    }
    
    @Override
    public void initialize(ClientGatewayContext context, Object stateToResume) {
    }
    
    @Override
    public Serializable shutdown() {
        return null;
    }

    @Override
    public void onPrivateMessage(String userRef, String segment, Object data) {
    }

    @Override
    public void onPublicMessage(String segment, Object data) {
    }
}

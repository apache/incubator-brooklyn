package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.io.Serializable;

import com.cloudsoftcorp.monterey.network.api.MediationSegmentService;
import com.cloudsoftcorp.monterey.network.api.MediationSegmentServiceFactory;
import com.cloudsoftcorp.monterey.network.api.SegmentServiceContext;
import com.cloudsoftcorp.monterey.network.api.SegmentStateBackup;
import com.cloudsoftcorp.monterey.network.api.SenderReference;

public class SimpleMediationSegmentService implements MediationSegmentService {

    public static class Factory implements MediationSegmentServiceFactory {
        public MediationSegmentService newSegmentService(String segment) {
            return new SimpleMediationSegmentService();
        }
        public SegmentStateBackup newSegmentBackup(String segment) {
            return null; // Unsupported
        }
    }
    
    @Override
    public void initialize(SegmentServiceContext context, Object stateToResume) {
    }

    @Override
    public Serializable shutdown() {
        return null;
    }
    
    @Override
    public void doMediation(SenderReference sender, Object data) {
    }
}

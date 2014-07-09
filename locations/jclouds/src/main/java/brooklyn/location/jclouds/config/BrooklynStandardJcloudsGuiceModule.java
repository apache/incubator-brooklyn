package brooklyn.location.jclouds.config;

import org.jclouds.softlayer.compute.functions.VirtualGuestToNodeMetadata;

import com.google.inject.AbstractModule;

public class BrooklynStandardJcloudsGuiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(VirtualGuestToNodeMetadata.class).to(
                Class.class.cast(SoftLayerFastVirtualGuestToNodeMetadata.class));
    }

}

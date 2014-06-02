package brooklyn.location.access;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.net.HostAndPort;

public class PortForwardManagerClient implements PortForwardManager {

    protected final Supplier<PortForwardManager> delegateSupplier;
    private transient volatile PortForwardManager _delegate;
    
    protected PortForwardManagerClient(Supplier<PortForwardManager> supplier) {
        this.delegateSupplier = supplier;
    }
    
    /** creates an instance given a supplier; 
     * the supplier should be brooklyn-persistable, that is to say
     * references should be in terms of entities/locations 
     * which can retrieve an authoritative source even under cloning */
    public static PortForwardManager fromSupplier(Supplier<PortForwardManager> supplier) {
        return new PortForwardManagerClient(supplier);
    }

    /** creates an instance given an entity and an interface method it implements to retrieve the PortForwardManager */ 
    public static PortForwardManager fromMethodOnEntity(final Entity entity, final String getterMethodOnEntity) {
        Preconditions.checkNotNull(entity);
        Preconditions.checkNotNull(getterMethodOnEntity);
        return new PortForwardManagerClient(new Supplier<PortForwardManager>() {
            @Override
            public PortForwardManager get() {
                PortForwardManager result;
                try {
                    result = (PortForwardManager) entity.getClass().getMethod(getterMethodOnEntity).invoke(entity);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    throw new IllegalStateException("Cannot invoke "+getterMethodOnEntity+" on "+entity+" ("+entity.getClass()+"): "+e, e);
                }
                if (result==null)
                    throw new IllegalStateException("No PortForwardManager available via "+getterMethodOnEntity+" on "+entity+" (returned null)");
                return result;
            }
        });
    }
    
    protected PortForwardManager getDelegate() {
        if (_delegate==null) {
            _delegate = delegateSupplier.get();
        }
        return _delegate;
    }

    public int acquirePublicPort(String publicIpId) {
        return getDelegate().acquirePublicPort(publicIpId);
    }

    public PortMapping acquirePublicPortExplicit(String publicIpId, int port) {
        return getDelegate().acquirePublicPortExplicit(publicIpId, port);
    }

    public PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort) {
        return getDelegate().getPortMappingWithPublicSide(publicIpId, publicPort);
    }

    public Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId) {
        return getDelegate().getPortMappingWithPublicIpId(publicIpId);
    }

    public PortMapping forgetPortMapping(String publicIpId, int publicPort) {
        return getDelegate().forgetPortMapping(publicIpId, publicPort);
    }

    public boolean forgetPortMapping(PortMapping m) {
        return getDelegate().forgetPortMapping(m);
    }

    public void recordPublicIpHostname(String publicIpId, String hostnameOrPublicIpAddress) {
        getDelegate().recordPublicIpHostname(publicIpId, hostnameOrPublicIpAddress);
    }

    public String getPublicIpHostname(String publicIpId) {
        return getDelegate().getPublicIpHostname(publicIpId);
    }

    public boolean forgetPublicIpHostname(String publicIpId) {
        return getDelegate().forgetPublicIpHostname(publicIpId);
    }

    public HostAndPort getPublicHostAndPort(PortMapping m) {
        return getDelegate().getPublicHostAndPort(m);
    }

    public int acquirePublicPort(String publicIpId, Location l, int privatePort) {
        return getDelegate().acquirePublicPort(publicIpId, l, privatePort);
    }

    public HostAndPort lookup(Location l, int privatePort) {
        return getDelegate().lookup(l, privatePort);
    }

    public void associate(String publicIpId, int publicPort, Location l, int privatePort) {
        getDelegate().associate(publicIpId, publicPort, l, privatePort);
    }

    public Collection<PortMapping> getLocationPublicIpIds(Location l) {
        return getDelegate().getLocationPublicIpIds(l);
    }

    public PortMapping getPortMappingWithPrivateSide(Location l, int privatePort) {
        return getDelegate().getPortMappingWithPrivateSide(l, privatePort);
    }
    
    @Override
    public boolean isClient() {
        return true;
    }
    
}

package brooklyn.entity.network.bind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;

/**
 * This sets up a BIND DNS server.
 */
public class BindDnsServerImpl extends SoftwareProcessImpl implements BindDnsServer {

    protected static final Logger LOG = LoggerFactory.getLogger(BindDnsServerImpl.class);

    public BindDnsServerImpl() {
        super();
    }

    @Override
    public Class<BindDnsServerDriver> getDriverInterface() {
        return BindDnsServerDriver.class;
    }

    @Override
    public BindDnsServerDriver getDriver() {
        return (BindDnsServerDriver) super.getDriver();
    }

}

package brooklyn.entity.network.bind;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * This sets up a BIND DNS server.
 */
@Catalog(name="BIND", description="BIND is an Internet Domain Name Server.", iconUrl="classpath:///isc-logo.png")
@ImplementedBy(BindDnsServerImpl.class)
public interface BindDnsServer extends SoftwareProcess {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("filter")
    ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = new BasicConfigKey(Predicate.class,
            "bind.entity.filter", "Filter for entities which will have locations added to DNS", Predicates.instanceOf(SoftwareProcess.class));

    @SetFromFlag("subnet")
    ConfigKey<String> MANAGEMENT_CIDR = new BasicConfigKey<String>(String.class, "bind.access.cidr", "Subnet CIDR allowed to access DNS", "0.0.0.0/0");
    // TODO should default be a /0 or use brooklyn management CIDR?

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("hostnameSensor")
    ConfigKey<AttributeSensor<String>> HOSTNAME_SENSOR = new BasicConfigKey(AttributeSensor.class, "bind.sensor.hostname", "Sensor on managed entities that reports the hostname", Attributes.HOSTNAME);

    PortAttributeSensorAndConfigKey DNS_PORT = new PortAttributeSensorAndConfigKey("bind.port", "BIND DNS port for TCP and UDP", PortRanges.fromString("53"));

}

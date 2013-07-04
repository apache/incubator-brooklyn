package brooklyn.entity.network.bind;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.util.text.Strings;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * This sets up a BIND DNS server.
 */
public class BindDnsServerImpl extends SoftwareProcessImpl implements BindDnsServer {

    protected static final Logger LOG = LoggerFactory.getLogger(BindDnsServerImpl.class);
    protected static final String RESOURCES_CLASSPATH = "classpath://brooklyn/entity/network/bind/";

    private AtomicLong serial = new AtomicLong(System.currentTimeMillis());
    private Object[] mutex = new Object[0];
    private DynamicGroup entities;
    private AbstractMembershipTrackingPolicy policy;
    private Multimap<Location, Entity> entityLocations = HashMultimap.create();
    private ConcurrentMap<String, String> addressMappings = Maps.newConcurrentMap();
    protected List<InetAddress> addresses = Lists.newArrayList();

    public BindDnsServerImpl() {
        super();
    }

    public String getManagementCidr() {
        return getConfig(MANAGEMENT_CIDR);
    }

    public Integer getDnsPort() {
        return getAttribute(DNS_PORT);
    }

    public String getDomainName() {
        return getConfig(DOMAIN_NAME);
    }

    public long getSerial() {
        return serial.incrementAndGet();
    }

    @Override
    public void init() {
        entities = addChild(EntitySpecs.spec(DynamicGroup.class)
                .configure("entityFilter", getConfig(ENTITY_FILTER)));
    }

    @Override
    public Class<BindDnsServerDriver> getDriverInterface() {
        return BindDnsServerDriver.class;
    }

    public Map<String, String> getAddressMappings() {
        return addressMappings;
    }

    @Override
    public BindDnsServerDriver getDriver() {
        return (BindDnsServerDriver) super.getDriver();
    }

    @Override
    public void connectSensors() {
        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
    }

    @Override
    protected void preStart() {
        Map<?, ?> flags = MutableMap.builder()
                .put("name", "Address tracker")
                .put("sensorsToTrack", ImmutableSet.of(getConfig(HOSTNAME_SENSOR)))
                .build();
        policy = new AbstractMembershipTrackingPolicy(flags) {
            @Override
            protected void onEntityChange(Entity member) { added(member); }
            @Override
            protected void onEntityAdded(Entity member) { } // Ignore
            @Override
            protected void onEntityRemoved(Entity member) { removed(member); }
        };

        addPolicy(policy);
        policy.setGroup(entities);
    }

    @Override
    public void postStart() {
        update();
    }

    public void added(Entity member) {
        synchronized (mutex) {
            Optional<Location> location = Iterables.tryFind(member.getLocations(), Predicates.instanceOf(SshMachineLocation.class));
            String hostname = member.getAttribute(getConfig(HOSTNAME_SENSOR));
            if (location.isPresent() && Strings.isNonBlank(hostname)) {
                SshMachineLocation machine = (SshMachineLocation) location.get();
                String address = machine.getAddress().getHostAddress();
                if (!entityLocations.containsKey(machine)) {
                    entityLocations.put(machine, member);
                    addressMappings.putIfAbsent(address, hostname);
                    if (getAttribute(Startable.SERVICE_UP)) {
                        update();
                    }
                    configure(machine);
                    LOG.info("{} added at location {} with name {}", new Object[] { member, machine, hostname });
                }
            } else {
                LOG.warn("added({}) called but entity not ready", member);
            }
        }
    }

    public void removed(Entity member) {
        synchronized (mutex) {
            Optional<Location> location = Iterables.tryFind(member.getLocations(), Predicates.instanceOf(SshMachineLocation.class));
            SshMachineLocation machine = (SshMachineLocation) location.get();
            entityLocations.remove(machine, member);
            if (!entityLocations.containsKey(machine)) {
                addressMappings.remove(machine.getAddress().getHostAddress());
            }
            update();
        }
    }

    public void update() {
        Optional<Location> location = Iterables.tryFind(getLocations(), Predicates.instanceOf(SshMachineLocation.class));
        SshMachineLocation machine = (SshMachineLocation) location.get();
        String[] templateList = new String[] { "domain.zone", "named.conf" };
        for (String fileName : templateList) {
            String contents = ((BindDnsServerSshDriver) getDriver()).processTemplate(RESOURCES_CLASSPATH + fileName);
            machine.copyTo(new ByteArrayInputStream(contents.getBytes()), "/tmp/" + fileName);
        }
        machine.execScript("update bind config", ImmutableList.of(
                CommonCommands.sudo("mv /tmp/named.conf /etc/named.conf"),
                CommonCommands.sudo("mv /tmp/domain.zone /var/named/domain.zone"),
                CommonCommands.sudo("service named restart")));

        LOG.info("updated named configuration and zone file for '{}' on {}", getDomainName(), this);
    }

    public void configure(SshMachineLocation machine) {
        String contents = ((BindDnsServerSshDriver) getDriver()).processTemplate(RESOURCES_CLASSPATH + "resolv.conf");
        machine.copyTo(new ByteArrayInputStream(contents.getBytes()), "/tmp/resolv.conf");
        machine.execScript("update resolver config", ImmutableList.of(CommonCommands.sudo("mv /tmp/resolv.conf /etc/resolv.conf")));
        LOG.info("configured resolver on {}", machine);
    }
}

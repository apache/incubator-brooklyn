package brooklyn.entity.dns

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.WebAppService
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.geo.HostGeoInfo
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Throwables
import com.google.common.collect.ImmutableMap
import com.google.common.util.concurrent.ThreadFactoryBuilder


abstract class AbstractGeoDnsService extends AbstractEntity {
    protected static final Logger log = LoggerFactory.getLogger(AbstractGeoDnsService.class);

    @SetFromFlag
    protected Entity targetEntityProvider;

    protected Map<Entity, HostGeoInfo> targetHosts = Collections.synchronizedMap(new LinkedHashMap<Entity, HostGeoInfo>());
    
    @SetFromFlag("pollPeriod")
    public static final ConfigKey<Long> POLL_PERIOD = new BasicConfigKey<Long>(Long.class, "geodns.pollperiod", "Poll period (in milliseconds) for refreshing target hosts", 5000L)
    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    public static final Sensor SERVICE_UP = Startable.SERVICE_UP;
    public static final BasicAttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final BasicAttributeSensor TARGETS =
        [ String, "geodns.targets", "Map of targets currently being managed (entity ID to URL)" ];

    // We complain when we encounter a target entity for whom we can't derive geo information; the commonest case is a
    // transient condition between the time the entity is created and the time it is started (at which point the location is
    // specified). This set contains those entities we've complained about already, to avoid repetitive logging.
    transient protected Set<Entity> entitiesWithoutGeoInfo = new HashSet<Entity>();
    

    public AbstractGeoDnsService(Map properties = [:], Entity parent = null) {
        super(properties, parent);
    }
    
    @Override
    public void onManagementBecomingMaster() {
        super.onManagementBecomingMaster();
        beginPoll();
    }
    @Override
    public void onManagementNoLongerMaster() {
        endPoll();
        super.onManagementNoLongerMaster();
    }

    @Override
    public void destroy() {
        setServiceState(Lifecycle.DESTROYED);
        super.destroy();
    }
        
    public void setServiceState(Lifecycle state) {
        setAttribute(HOSTNAME, getHostname());
        setAttribute(SERVICE_STATE, state);
        setAttribute(SERVICE_UP, state==Lifecycle.RUNNING);
    }
    
    /** if target is a group, its members are searched; otherwise its children are searched */
    public void setTargetEntityProvider(final Entity entityProvider) {
        this.targetEntityProvider = checkNotNull(entityProvider, "targetEntityProvider");
    }
    
    // TODO: remove polling once locations can be determined via subscriptions
    ScheduledFuture poll;
    protected void beginPoll() {
        if (log.isDebugEnabled()) log.debug("GeoDns $this starting poll");
        if (poll!=null) {
            log.warn("GeoDns duplicate call to beginPoll, ignoring")
            return;
        }
        if (targetEntityProvider==null) {
            log.warn("GeoDns $this has no targetEntityProvider; polling will have no-effect until it is set")
        }
        
        // TODO Should re-use the execution manager's thread pool, somehow
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-geodnsservice-%d")
                .build();
        poll = Executors.newSingleThreadScheduledExecutor(threadFactory).scheduleAtFixedRate(
            new Runnable() {
                public void run() {
                    try {
                        refreshGroupMembership();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable t) {
                        log.warn("Error refreshing group membership", t)
                        Throwables.propagate(t)
                    }
                }
            }, 0, getConfig(POLL_PERIOD), TimeUnit.MILLISECONDS
        );
    }
    
    protected void endPoll() {
        if (poll!=null) {
            if (log.isDebugEnabled()) log.debug("GeoDns $this ending poll");
            poll.cancel(true);
            poll = null;
        }
    }

    /** should set up so these hosts are targeted, and setServiceState appropriately */
    protected abstract void reconfigureService(Collection<HostGeoInfo> targetHosts);
    
    /** should return the hostname which this DNS service is configuring */
    public abstract String getHostname();
    
    // TODO: remove group member polling once locations can be determined via subscriptions
    protected void refreshGroupMembership() {
        try {
            if (log.isDebugEnabled()) log.debug("GeoDns $this refreshing targets");
            if (targetEntityProvider == null)
                return;
            if (targetEntityProvider instanceof DynamicGroup)
                ((DynamicGroup) targetEntityProvider).rescanEntities();
            Set<Entity> pool = [] + (targetEntityProvider instanceof Group ? targetEntityProvider.members : targetEntityProvider.children);
            
            boolean changed = false;
            Set<Entity> previousOnes = [] + targetHosts.keySet();
            for (Entity e: pool) {
                if (previousOnes.remove(e)) continue;
                changed |= addTargetHost(e, false);
            }
            //anything left in previousOnes is no longer applicable
            for (Entity e: previousOnes) {
                changed = true;
                removeTargetHost(e, false);
            }
            
            if (changed)
                update();
            
        } catch (Exception e) {
            log.error("Problem refreshing group membership: $e", e);
        }
    }
    
    /** returns if host is added */
    protected boolean addTargetHost(Entity e, boolean doUpdate) {
        if (targetHosts.containsKey(e)) {
            log.warn("GeoDns ignoring already-added entity $e");
            return;
        }
        //add it if it is valid
        try {
            String hostname = e.getAttribute(Attributes.HOSTNAME);
            String url = e.getAttribute(WebAppService.ROOT_URL);
            if (url!=null) {
                URL u = new URL(url);
                if (hostname==null) {
                    if (!entitiesWithoutGeoInfo.contains(e))  //don't log repeatedly
                        log.warn("GeoDns using URL $url to redirect to $e (HOSTNAME attribute is preferred, but not available)");
                    hostname = u.host; 
                }
                if (u.port>0 && u.port!=80 && u.port!=443) {
                    if (!entitiesWithoutGeoInfo.contains(e))  //don't log repeatedly
                        log.warn("GeoDns detected non-standard port in URL $url for $e; forwarding may not work");
                }
            }
            if (hostname==null) {
                if (entitiesWithoutGeoInfo.add(e)) {
                    log.debug("GeoDns ignoring $e, will continue scanning (no hostname or URL available)");
                }
                return;
            }
            HostGeoInfo geoH = HostGeoInfo.fromIpAddress(InetAddress.getByName(hostname));
            if (geoH == null) {
                if (entitiesWithoutGeoInfo.add(e)) {
                    log.warn("GeoDns ignoring $e (no geography info available for $hostname)");
                }
                return;
            }
            HostGeoInfo geoE = HostGeoInfo.fromEntity(e)
            if (geoE!=null) {
                //geo info set for both; prefer H, but warn if they differ dramatially
                if ((Math.abs(geoH.latitude-geoE.latitude)>3) ||
                        (Math.abs(geoH.longitude-geoE.longitude)>3) ) {
                    log.warn("GeoDns mismatch, $e is in $geoE but hosts URL in $geoH");
                }
            }
            
            entitiesWithoutGeoInfo.remove(e);
            log.info("GeoDns adding $e at $geoH"+(url!=null ? " (downstream listening on $url)" : ""));
            targetHosts.put(e, geoH);
            if (doUpdate) update();
            return true;
        } catch (Exception ee) {
            log.warn("GeoDns ignoring $e (error analysing location, $ee");
            return false;
        }
    }

    /** remove if host removed */
    protected boolean removeTargetHost(Entity e, boolean doUpdate) {
        if (targetHosts.remove(e)) {
            AbstractGeoDnsService.log.info("GeoDns removing reference to $e");
            if (doUpdate) update();
            return true;
        }
        return false;
    }
    
    protected void update() {
        Map<Entity, HostGeoInfo> m;
        synchronized(targetHosts) { m = ImmutableMap.copyOf(targetHosts); }
        
        Map<String,String> entityIdToUrl = [:]
        m.each { Entity k, HostGeoInfo v -> entityIdToUrl.put(k.id, v.address) }
        
        reconfigureService(new LinkedHashSet<HostGeoInfo>(m.values()));
        
        setAttribute(TARGETS, entityIdToUrl);
    }
    
}

package brooklyn.policy.ha;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

/**
 * Detects when members of a group have failed/recovered, and emits ENTITY_FAILED or 
 * ENTITY_RECOVERED accordingly.
 * 
 * This policy should be associated with a group to monitor its members:
 * <pre>
 * {@code
 *     group.addPolicy(new MemberFailureDetectionPolicy(...));
 * }
 * </pre>
 * 
 * Basic "failure" is defined as the service being "running" but isUp having flipped from 
 * true to false. 
 * 
 * These criteria can be further configured using "onlyReportIfPreviouslyUp" and
 * "useServiceStateRunning".
 * 
 * @author aled
 */
public class MemberFailureDetectionPolicy extends AbstractPolicy {

    // TODO Remove duplication between this and ServiceFailureDetection.
    // This could be re-written to use the latter. Or we could even deprecate
    // this in favour of the latter.
    
    private static final Logger LOG = LoggerFactory.getLogger(MemberFailureDetectionPolicy.class);

    @SetFromFlag(defaultVal="true")
    private boolean onlyReportIfPreviouslyUp;
    
    @SetFromFlag(defaultVal="true")
    private boolean useServiceStateRunning;
    
    @SetFromFlag
    private Predicate<? super Entity> memberFilter;
    
    private final Map<Entity, Long> memberFailures = Maps.newLinkedHashMap();
    private final Map<Entity, Long> memberLastUps = Maps.newLinkedHashMap();
    private final Map<Entity, Boolean> memberIsUps = Maps.newLinkedHashMap();
    private final Map<Entity, Lifecycle> memberStates = Maps.newLinkedHashMap();

    public MemberFailureDetectionPolicy() {
        this(MutableMap.<String,Object>of());
    }
    
    public MemberFailureDetectionPolicy(Map<String,?> flags) {
        super(flags);
        
        if (memberFilter == null) memberFilter = Predicates.alwaysTrue();
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        
        if (useServiceStateRunning) {
            subscribeToMembers((Group)entity, Attributes.SERVICE_STATE, new SensorEventListener<Lifecycle>() {
                @Override public void onEvent(SensorEvent<Lifecycle> event) {
                    if (!memberFilter.apply(event.getSource())) return;
                    onMemberStatus(event.getSource(), event.getValue());
                }
            });
        }
        
        subscribeToMembers((Group)entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                if (!memberFilter.apply(event.getSource())) return;
                onMemberIsUp(event.getSource(), event.getValue());
            }
        });
        
        subscribe(entity, Changeable.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                onMemberRemoved(event.getValue());
            }
        });
        
        subscribe(entity, Changeable.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                if (!memberFilter.apply(event.getSource())) return;
                onMemberAdded(event.getValue());
            }
        });
        
        for (Entity member : ((Group)entity).getMembers()) {
            if (!memberFilter.apply(member)) continue;
            onMemberAdded(member);
        }
    }
    
    private synchronized void onMemberIsUp(Entity member, Boolean isUp) {
        if (isUp != null) {
            Boolean old = memberIsUps.put(member, isUp);
            if (isUp) {
                memberLastUps.put(member, System.currentTimeMillis());
            }
            if (!Objects.equal(old, isUp)) {
                checkMemberHealth(member);
            }
        }
    }
    
    private synchronized void onMemberStatus(Entity member, Lifecycle status) {
        if (status != null) {
            Lifecycle old = memberStates.put(member, status);
            if (!Objects.equal(old, status)) {
                checkMemberHealth(member);
            }
        }
    }
    
    private synchronized void onMemberAdded(Entity member) {
        if (useServiceStateRunning) {
            Lifecycle status = member.getAttribute(Attributes.SERVICE_STATE);
            onMemberStatus(member, status);
        }
        
        Boolean isUp = member.getAttribute(Startable.SERVICE_UP);
        onMemberIsUp(member, isUp);
    }
    
    private synchronized void onMemberRemoved(Entity member) {
        memberStates.remove(member);
        memberIsUps.remove(member);
        memberLastUps.remove(member);
        memberFailures.remove(member);
    }
    
    private synchronized void checkMemberHealth(Entity member) {
        Long lastUpTime = memberLastUps.get(member);
        Boolean isUp = memberIsUps.get(member);
        Lifecycle status = memberStates.get(member);
        boolean failed = 
                (useServiceStateRunning && status == Lifecycle.ON_FIRE) ||
                (Boolean.FALSE.equals(isUp) &&
                        (useServiceStateRunning ? status == Lifecycle.RUNNING : true) && 
                        (onlyReportIfPreviouslyUp ? lastUpTime != null : true));
        boolean recovered = 
                (useServiceStateRunning ? status == Lifecycle.RUNNING : true) && 
                Boolean.TRUE.equals(isUp);

        String description = String.format("location=%s; isUp=%s; status=%s; lastReportedUp=%s; timeNow=%s", 
                member.getLocations(), 
                (isUp != null ? isUp : "<unreported>"),
                (status != null ? status : "<unreported>"),
                (lastUpTime != null ? Time.makeDateString(lastUpTime) : "<never>"),
                Time.makeDateString(System.currentTimeMillis()));

        if (memberFailures.containsKey(member)) {
            if (recovered) {
                LOG.info("{} health-check for {}, component recovered (from failure at {}): {}", 
                        new Object[] {this, member, Time.makeDateString(memberFailures.get(member)), description});
                entity.emit(HASensors.ENTITY_RECOVERED, new HASensors.FailureDescriptor(member, description));
                memberFailures.remove(member);
            } else if (failed) {
                if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, confirmed still failed: {}", new Object[] {this, member, description});
            } else {
                if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, in unconfirmed sate (previously failed): {}", new Object[] {this, member, description});
            }
        } else if (failed) {
            LOG.info("{} health-check for {}, component failed: {}", new Object[] {this, member, description});
            memberFailures.put(member, System.currentTimeMillis());
            entity.emit(HASensors.ENTITY_FAILED, new HASensors.FailureDescriptor(member, description));
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, either healthy or insufficient data: {}", new Object[] {this, member, description});
        }
    }
}

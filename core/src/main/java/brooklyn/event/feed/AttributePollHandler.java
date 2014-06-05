package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.time.Duration;

/**
 * Handler for when polling an entity's attribute. On each poll result the entity's attribute is set.
 * 
 * Calls to onSuccess and onError will happen sequentially, but may be called from different threads 
 * each time. Note that no guarantees of a synchronized block exist, so additional synchronization 
 * would be required for the Java memory model's "happens before" relationship.
 * 
 * @author aled
 */
public class AttributePollHandler<V> implements PollHandler<V> {

    public static final Logger log = LoggerFactory.getLogger(AttributePollHandler.class);

    private final FeedConfig<V,?,?> config;
    private final EntityLocal entity;
    @SuppressWarnings("rawtypes")
    private final AttributeSensor sensor;
    private final AbstractFeed feed;
    
    // allow 30 seconds before logging at WARN, if there has been no success yet;
    // after success WARN immediately
    // TODO these should both be configurable
    private Duration logWarningGraceTimeOnStartup = Duration.THIRTY_SECONDS;
    private Duration logWarningGraceTime = Duration.millis(0);
    
    // internal state to look after whether to log warnings
    private volatile Long lastSuccessTime = null;
    private volatile Long currentProblemStartTime = null;
    private volatile boolean currentProblemLoggedAsWarning = false;
    private volatile boolean lastWasProblem = false;
    
    public AttributePollHandler(FeedConfig<V,?,?> config, EntityLocal entity, AbstractFeed feed) {
        this.config = checkNotNull(config, "config");
        this.entity = checkNotNull(entity, "entity");
        this.sensor = checkNotNull(config.getSensor(), "sensor");
        this.feed = checkNotNull(feed, "feed");
    }

    @Override
    public boolean checkSuccess(V val) {
        // Always true if no checkSuccess predicate was configured.
        return !config.hasCheckSuccessHandler() || config.getCheckSuccess().apply(val);
    }

    @Override
    public void onSuccess(V val) {
        if (lastWasProblem) {
            if (currentProblemLoggedAsWarning) { 
                log.info("Success (following previous problem) reading "+getBriefDescription());
            } else {
                log.debug("Success (following previous problem) reading "+getBriefDescription());
            }
            lastWasProblem = false;
            currentProblemStartTime = null;
            currentProblemLoggedAsWarning = false;
        }
        lastSuccessTime = System.currentTimeMillis();
        if (log.isTraceEnabled()) log.trace("poll for {} got: {}", new Object[] {getBriefDescription(), val});
        
        try {
            setSensor(transformValueOnSuccess(val));
        } catch (Exception e) {
            if (feed.isConnected()) {
                log.warn("unable to compute "+getBriefDescription()+"; on val="+val, e);
            } else {
                if (log.isDebugEnabled()) log.debug("unable to compute "+getBriefDescription()+"; val="+val+" (when inactive)", e);
            }
        }
    }

    /** allows post-processing, such as applying a success handler; 
     * default applies the onSuccess handler (which is recommended) */
    protected Object transformValueOnSuccess(V val) {
        return config.hasSuccessHandler() ? config.getOnSuccess().apply(val) : val;
    }

    @Override
    public void onFailure(V val) {
        if (!config.hasFailureHandler()) {
            onException(new Exception("checkSuccess of "+this+" for "+getBriefDescription()+" was false but poller has no failure handler"));
        } else {
            logProblem("failure", val);

            try {
                setSensor(config.hasFailureHandler() ? config.getOnFailure().apply((V)val) : val);
            } catch (Exception e) {
                if (feed.isConnected()) {
                    log.warn("Error computing " + getBriefDescription() + "; val=" + val+": "+ e, e);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("Error computing " + getBriefDescription() + "; val=" + val + " (when inactive)", e);
                }
            }
        }
    }

    @Override
    public void onException(Exception exception) {
        if (!feed.isConnected()) {
            if (log.isTraceEnabled()) log.trace("Read of {} in {} gave exception (while not connected or not yet connected): {}", new Object[] {this, getBriefDescription(), exception});
        } else {
            logProblem("exception", exception);
        }

        if (config.hasExceptionHandler()) {
            try {
                setSensor( config.getOnException().apply(exception) );
            } catch (Exception e) {
                if (feed.isConnected()) {
                    log.warn("unable to compute "+getBriefDescription()+"; on exception="+exception, e);
                } else {
                    if (log.isDebugEnabled()) log.debug("unable to compute "+getBriefDescription()+"; exception="+exception+" (when inactive)", e);
                }
            }
        }
    }

    protected void logProblem(String type, Object val) {
        if (lastWasProblem && currentProblemLoggedAsWarning) {
            if (log.isTraceEnabled())
                log.trace("Recurring {} reading {} in {}: {}", new Object[] {type, this, getBriefDescription(), val});
        } else {
            long nowTime = System.currentTimeMillis();
            // get a non-volatile value
            Long currentProblemStartTimeCache = currentProblemStartTime;
            long expiryTime = 
                    lastSuccessTime!=null ? lastSuccessTime+logWarningGraceTime.toMilliseconds() :
                    currentProblemStartTimeCache!=null ? currentProblemStartTimeCache+logWarningGraceTimeOnStartup.toMilliseconds() :
                    nowTime+logWarningGraceTimeOnStartup.toMilliseconds();
            if (!lastWasProblem) {
                if (expiryTime <= nowTime) {
                    currentProblemLoggedAsWarning = true;
                    log.warn("Read of " + getBriefDescription() + " gave " + type + ": " + val);
                    if (log.isDebugEnabled() && val instanceof Throwable)
                        log.debug("Trace for "+type+" reading "+getBriefDescription()+": "+val, (Throwable)val);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("Read of " + getBriefDescription() + " gave " + type + " (in grace period): " + val);
                }
                lastWasProblem = true;
                currentProblemStartTime = nowTime;
            } else {
                if (expiryTime <= nowTime) {
                    currentProblemLoggedAsWarning = true;
                    log.warn("Read of " + getBriefDescription() + " gave " + type + 
                            " (grace period expired, occurring for "+Duration.millis(nowTime - currentProblemStartTimeCache)+", " +
                            (config.hasExceptionHandler() ? "" : ", no exception handler set for sensor")+
                            ")"+
                            ": " + val);
                    if (log.isDebugEnabled() && val instanceof Throwable)
                        log.debug("Trace for "+type+" reading "+getBriefDescription()+": "+val, (Throwable)val);
                } else {
                    if (log.isDebugEnabled()) 
                        log.debug("Recurring {} reading {} in {} (still in grace period): {}", new Object[] {type, this, getBriefDescription(), val});
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void setSensor(Object v) {
        if (v == FeedConfig.UNCHANGED) {
            // nothing
        } else if (v == FeedConfig.REMOVE) {
            ((EntityInternal)entity).removeAttribute(sensor);
        } else if (sensor == FeedConfig.NO_SENSOR) {
            // nothing
        } else {
            entity.setAttribute(sensor, TypeCoercions.coerce(v, sensor.getType()));
        }
    }

    @Override
    public String toString() {
        return super.toString()+"["+getDescription()+"]";
    }
    
    @Override
    public String getDescription() {
        return sensor.getName()+" @ "+entity.getId()+" <- "+config;
    }
    
    protected String getBriefDescription() {
        return ""+entity+"->"+(sensor==FeedConfig.NO_SENSOR ? "(dynamic sensors)" : ""+sensor);
    }
        
}

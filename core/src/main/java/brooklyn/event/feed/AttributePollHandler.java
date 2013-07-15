package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    public AttributePollHandler(FeedConfig config, EntityLocal entity, AbstractFeed feed) {
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
                log.info("Success (following previous problem) reading "+entity+"->"+sensor);
            } else {
                log.debug("Success (following previous problem) reading "+entity+"->"+sensor);
            }
            lastWasProblem = false;
            currentProblemStartTime = null;
            currentProblemLoggedAsWarning = false;
        }
        lastSuccessTime = System.currentTimeMillis();
        if (log.isTraceEnabled()) log.trace("poll for {}->{} got: {}", new Object[] {entity, sensor, val});
        
        try {
            Object v = transformValue(val);
            if (v != PollConfig.UNSET) {
                entity.setAttribute(sensor, v);
            }
        } catch (Exception e) {
            if (feed.isConnected()) {
                log.warn("unable to compute "+entity+"->"+sensor+"; on val="+val, e);
            } else {
                if (log.isDebugEnabled()) log.debug("unable to compute "+entity+" ->"+sensor+"; val="+val+" (when inactive)", e);
            }
        }
    }

    @Override
    public void onFailure(V val) {
        if (!config.hasFailureHandler()) {
            onException(new Exception("checkSuccess of "+this+" from "+entity+" was false but poller has no failure handler"));
        } else {
            logProblem("failure", val);

            try {
                Object v = coerce(config.getOnFailure().apply(val));
                if (v != PollConfig.UNSET) {
                    entity.setAttribute(sensor, v);
                }
            } catch (Exception e) {
                if (feed.isConnected()) {
                    log.warn("Error computing " + entity + "->" + sensor + "; val=" + val+": "+ e, e);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("Error computing " + entity + " ->" + sensor + "; val=" + val + " (when inactive)", e);
                }
            }
        }
    }

    /**
     * @deprecated since 0.6; use {@link #onException(Exception)}
     */
    @Override
    public void onError(Exception error) {
        onException(error);
    }

    @Override
    public void onException(Exception exception) {
        if (!feed.isConnected()) {
            if (log.isDebugEnabled()) log.debug("Read of {} from {} gave exception (while not connected or not yet connected): {}", new Object[] {this, entity, exception});
        } else {
            logProblem("failure", exception);
        }

        if (config.hasExceptionHandler()) {
            try {
                Object v = transformError(exception);
                if (v != PollConfig.UNSET) {
                    entity.setAttribute(sensor, v);
                }
            } catch (Exception e) {
                if (feed.isConnected()) {
                    log.warn("unable to compute "+entity+"->"+sensor+"; on exception="+exception, e);
                } else {
                    if (log.isDebugEnabled()) log.debug("unable to compute "+entity+" ->"+sensor+"; exception="+exception+" (when inactive)", e);
                }
            }
        }
    }

    protected void logProblem(String type, Object val) {
        if (lastWasProblem && currentProblemLoggedAsWarning) {
            if (log.isDebugEnabled())
                log.debug("Recurring "+type+" reading " + this + " from " + entity + ": " + val);
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
                    log.warn("Read of " + entity + "->" + sensor + " gave " + type + ": " + val);
                    if (log.isDebugEnabled() && val instanceof Throwable)
                        log.debug("Trace for "+type+" reading "+entity+"->"+sensor+": "+val, (Throwable)val);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("Read of " + entity + "->" + sensor + " gave " + type + 
                                " (in grace period)" +
                                ": " + val);
                }
                lastWasProblem = true;
                currentProblemStartTime = nowTime;
            } else {
                if (expiryTime <= nowTime) {
                    currentProblemLoggedAsWarning = true;
                    log.warn("Read of " + entity + "->" + sensor + " gave " + type + 
                            " (grace period expired, occurring for "+Duration.millis(nowTime - currentProblemStartTimeCache)+")"+
                            ": " + val);
                    if (log.isDebugEnabled() && val instanceof Throwable)
                        log.debug("Trace for "+type+" reading "+entity+"->"+sensor+": "+val, (Throwable)val);
                } else {
                    log.debug("Recurring "+type+" reading " + this + " from " + entity + 
                            " (still in grace period)" +
                            ": " + val);
                }
            }
        }
    }
    
    /**
     * Does post-processing on the result of the actual poll, to convert it to the attribute's new value.
     * Or returns PollConfig.UNSET if the post-processing indicates that the attribute should not be changed.
     */
    protected Object transformValue(Object val) {
        if (config.hasSuccessHandler()) {
            return coerce(config.getOnSuccess().apply((V)val));
        } else {
            return coerce(val);
        }
    }
    
    /**
     * Does post-processing on a poll error, to convert it to the attribute's new value.
     * Or returns PollConfig.UNSET if the post-processing indicates that the attribute should not be changed.
     */
    protected Object transformError(Exception error) throws Exception {
        if (!config.hasExceptionHandler())
            throw new IllegalStateException("Attribute poll handler has no error handler, but attempted to transform error", error);
        return coerce(config.getOnException().apply(error));
    }

    private Object coerce(Object v) {
        if (v != PollConfig.UNSET) {
            return TypeCoercions.coerce(v, sensor.getType());
        } else {
            return v;
        }
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+sensor+" @ "+entity+" <- "+feed+"]";
    }
}

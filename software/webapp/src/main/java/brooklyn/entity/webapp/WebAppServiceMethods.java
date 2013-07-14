package brooklyn.entity.webapp;

import java.util.concurrent.TimeUnit;

import brooklyn.enricher.RollingTimeWindowMeanEnricher;
import brooklyn.enricher.TimeFractionDeltaEnricher;
import brooklyn.enricher.TimeWeightedDeltaEnricher;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.util.time.Duration;

public class WebAppServiceMethods implements WebAppServiceConstants {
    
    public static Duration DEFAULT_WINDOW_DURATION = Duration.TEN_SECONDS;
    
    public static void connectWebAppServerPolicies(EntityLocal entity) {
        connectWebAppServerPolicies(entity, DEFAULT_WINDOW_DURATION);
    }
    
    public static void connectWebAppServerPolicies(EntityLocal entity, Duration windowPeriod) {
        entity.addEnricher(TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(entity, REQUEST_COUNT, REQUESTS_PER_SECOND_LAST));
        
        if (windowPeriod!=null) {
            entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity, REQUESTS_PER_SECOND_LAST, 
                    REQUESTS_PER_SECOND_IN_WINDOW, windowPeriod));
        }
        
        entity.addEnricher(new TimeFractionDeltaEnricher<Integer>(entity, TOTAL_PROCESSING_TIME, PROCESSING_TIME_FRACTION_LAST, TimeUnit.MILLISECONDS));
        
        if (windowPeriod!=null) {
            entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity, PROCESSING_TIME_FRACTION_LAST, 
                    PROCESSING_TIME_FRACTION_IN_WINDOW, windowPeriod));
        }

    }
}

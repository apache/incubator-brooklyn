package brooklyn.config.render;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import groovy.lang.Closure;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** registry of hints for displaying items such as sensors, e.g. in the web console */
public class RendererHints {

    static Map<Object, Set<Hint>> registry = new LinkedHashMap<Object, Set<Hint>>();

    /** registers a hint against the given element (eg a sensor);
     * returns the element, for convenience when used in a with block after defining the element 
     */
    public synchronized static <T> T register(T element, Hint<T> hintForThatElement) {
        Set<Hint> set = registry.get(element);
        if (set == null) {
            set = new LinkedHashSet<Hint>();
            registry.put(element, set);
        }
        set.add(hintForThatElement);
        return element;
    }

    /** abstract superclass (marker) for 'hints' */
    public static abstract class Hint<T> {}

    /** this hint describes a named action possible on something, e.g. a sensor;
     * currently used in web client to show actions on sensors
     */
    public static class NamedActionWithUrl extends Hint<Sensor> {
        private final String actionName;
        private final Closure postProcessing;

        public NamedActionWithUrl(String actionName) {
            this(actionName, null);
        }

        public NamedActionWithUrl(String actionName, Closure postProcessing) {
            this.actionName = actionName;
            this.postProcessing = postProcessing;
        }

        public String getUrl(Entity e, AttributeSensor s) {
            return getUrlFromValue(e.getAttribute(s));
        }

        public String getActionName() {
            return actionName;
        }

        /** this is the method invoked by web console SensorSummary, at the moment */
        public String getUrlFromValue(Object v) {
            if (postProcessing != null) {
                v = postProcessing.call(v);
            }
            if (v != null) {
                return "" + v;
            }
            return null;
        }
    }

    public static synchronized Set<Hint> getHintsFor(Object element) {
         return getHintsFor(element, Hint.class);
    }

    public static synchronized Set<Hint> getHintsFor(Object element, Class<? extends Hint> optionalHintSuperClass) {
        Set<Hint> results = new LinkedHashSet<Hint>();
        Set<Hint> found = registry.get(element);
        if (found != null) {
            for (Hint h : found) {
                if (optionalHintSuperClass == null || optionalHintSuperClass.isAssignableFrom(h.getClass()))
                    results.add(h);
            }
        }
        return results;
    }
}

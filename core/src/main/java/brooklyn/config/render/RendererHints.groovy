package brooklyn.config.render;

import java.util.Map
import java.util.Set

import brooklyn.entity.Entity
import brooklyn.event.Sensor

/** registry of hints for displaying items such as sensors, e.g. in the web console */
public class RendererHints {

    static Map<Object,Set<Hint>> registry = [:];
    
    /** registers a hint against the given element (eg a sensor);
     * returns the element, for convenience when used in a with block after defining the element 
     */
    public synchronized static <T> T register(T element, Hint<T> hintForThatElement) {
        def set = registry.get(element);
        if (set==null) {
            set = new LinkedHashSet<Hint>();
            registry.put(element,  set);
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
        String actionName;
        Closure postProcessing;
        public NamedActionWithUrl(String actionName, Closure postProcessing=null) {
            this.actionName = actionName;
            this.postProcessing = postProcessing;
        }

        public String getUrl(Entity e, Sensor s) {
            return getUrlFromValue(e.getAttribute(s));
        }            
        public String getUrlFromValue(Object v) {
            if (postProcessing) v = postProcessing.call(v);
            if (v) return ""+v;
            return null;
        }
    }
    
    public static synchronized Set<Hint> getHintsFor(Object element, Class<? extends Hint> optionalHintSuperClass=Hint) {
        Set<Hint> results = []
        Set<Hint> found = registry.get(element);
        if (found) for (Hint h: found) {
            if (optionalHintSuperClass==null || optionalHintSuperClass.isAssignableFrom(h.getClass()))
                results << h;
        }
        return results;
    } 
    
}

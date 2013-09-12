package brooklyn.location.jclouds;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.HasShortName;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringShortener;
import brooklyn.util.text.Strings;

public class CloudMachineNamer {

    private ConfigBag setup;

    public CloudMachineNamer(ConfigBag setup) {
        this.setup = setup;
    }
    
    public String generateNewGroupId() {
        Object context = setup.peek(JcloudsLocationConfig.CALLER_CONTEXT);
        Entity entity = null;
        if (context instanceof Entity) entity = (Entity) context;
        
        StringShortener shortener = Strings.shortener().separator("-");
        shortener.append("system", "brooklyn");
        
        // randId often not necessary, as an 8-char hex identifier is added later (in jclouds? can we override?)
        // however it can be useful to have this early in the string, to prevent collisions in places where it is abbreviated 
        shortener.
            append("randId", Identifiers.makeRandomId(4));
        
        String user = System.getProperty("user.name");
        if (!"brooklyn".equals(user))
            // include user; unless the user is 'brooklyn', as 'brooklyn-brooklyn-' is just silly!
            shortener.append("user", user);
        
        if (entity!=null) {
            Application app = entity.getApplication();
            if (app!=null) {
                shortener.
                    append("app", shortName(app)).
                    append("appId", app.getId());
            }
            shortener.append("entity", shortName(entity)).
                append("entityId", entity.getId());
        } else if (context!=null) {
            shortener.append("context", context.toString());
        }
        
        shortener.
            truncate("user", 12).
            truncate("app", 16).
            truncate("entity", 16).
            truncate("appId", 4).
            truncate("entityId", 4).
            truncate("context", 12);
        
        shortener.
            canTruncate("user", 8).
            canTruncate("app", 5).
            canTruncate("entity", 5).
            canTruncate("system", 2).
            canTruncate("app", 3).
            canTruncate("entity", 3).
            canRemove("app").
            canTruncate("user", 4).
            canRemove("entity").
            canTruncate("context", 4).
            canTruncate("randId", 2).
            canRemove("user").
            canTruncate("appId", 2).
            canRemove("appId");
        
        int len = getMaxNameLength();
        // decrement by 9 chars because jclouds adds that (dash plus 8 for hex id)
        len -= 9;
        if (len<=0) return "";
        String s = shortener.getStringOfMaxLength(len);
        return sanitize(s).toLowerCase();
    }

    /** returns the max length of a VM name for the cloud specified in setup;
     * this value is typically decremented by 9 to make room for jclouds labels */
    public int getMaxNameLength() {
        if (setup.containsKey(JcloudsLocationConfig.VM_NAME_MAX_LENGTH)) {
            // if a length is set, use that
            return setup.get(JcloudsLocationConfig.VM_NAME_MAX_LENGTH);
        }
        
        // otherwise, for some known clouds which only allow a short name, use that length
        if ("vcloud".equals( setup.peek(JcloudsLocationConfig.CLOUD_PROVIDER) )) 
            return 24;
        if ("abiquo".equals( setup.peek(JcloudsLocationConfig.CLOUD_PROVIDER) )) 
            return 39;
        if ("google-compute-engine".equals( setup.peek(JcloudsLocationConfig.CLOUD_PROVIDER) ))
            return 39;
        // TODO other cloud max length rules
        
        // return the default
        return setup.get(JcloudsLocationConfig.VM_NAME_MAX_LENGTH);  
    }

    private String shortName(Object x) {
        if (x instanceof HasShortName) {
            return ((HasShortName)x).getShortName();
        }
        if (x instanceof Entity) {
            return ((Entity)x).getDisplayName();
        }
        return x.toString();
    }

    public static String sanitize(String s) {
        return s.replaceAll("[^_A-Za-z0-9]+", "-");
    }
}

package brooklyn.location.cloud;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.HasShortName;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringShortener;
import brooklyn.util.text.Strings;

public class CloudMachineNamer {

    protected final ConfigBag setup;
    int defaultMachineNameMaxLength = CloudLocationConfig.VM_NAME_MAX_LENGTH.getDefaultValue();
    int nameInGroupReservedLength = 9;

    public CloudMachineNamer(ConfigBag setup) {
        this.setup = setup;
    }
    
    public String generateNewMachineUniqueName() {
        return generateNewIdReservingLength(0);
    }
    
    public String generateNewGroupId() {
        return generateNewIdReservingLength(nameInGroupReservedLength);
    }
    
    protected String generateNewIdReservingLength(int lengthToReserve) {
        Object context = setup.peek(CloudLocationConfig.CALLER_CONTEXT);
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
        // decrement by e.g. 9 chars because jclouds adds that (dash plus 8 for hex id)
        len -= lengthToReserve;
        if (len<=0) return "";
        String s = shortener.getStringOfMaxLength(len);
        return sanitize(s).toLowerCase();
    }

    /** returns the max length of a VM name for the cloud specified in setup;
     * this value is typically decremented by 9 to make room for jclouds labels;
     * delegates to {@link #getCustomMaxNameLength()} when 
     * {@link CloudLocationConfig#VM_NAME_MAX_LENGTH} is not set */
    public int getMaxNameLength() {
        if (setup.containsKey(CloudLocationConfig.VM_NAME_MAX_LENGTH)) {
            // if a length is set, use that
            return setup.get(CloudLocationConfig.VM_NAME_MAX_LENGTH);
        }
        
        Integer custom = getCustomMaxNameLength();
        if (custom!=null) return custom;
        
        // return the default
        return defaultMachineNameMaxLength;  
    }
    
    public CloudMachineNamer lengthMaxPermittedForMachineName(int defaultMaxLength) {
        this.defaultMachineNameMaxLength = defaultMaxLength;
        return this;
    }

    /** number of chars to use or reserve for the machine identifier when constructing a group identifier;
     * defaults to 9, e.g. a hyphen and 8 random chars which is the jclouds model 
     * @return */
    public CloudMachineNamer lengthReservedForNameInGroup(int identifierRequiredLength) {
        this.nameInGroupReservedLength = identifierRequiredLength;
        return this;
    }
    
    /** method for overriding to provide custom logic when an explicit config key is not set */
    public Integer getCustomMaxNameLength() {
        return null;
    }

    protected String shortName(Object x) {
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

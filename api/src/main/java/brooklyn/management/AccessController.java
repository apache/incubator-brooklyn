package brooklyn.management;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

import com.google.common.annotations.Beta;

@Beta
public interface AccessController {

    // TODO Expect this class' methods to change, e.g. including the user doing the
    // provisioning or the provisioning parameters such as jurisdiction
    
    public static class Response {
        private static final Response ALLOWED = new Response(true, "");
        
        public static Response allowed() {
            return ALLOWED;
        }
        
        public static Response disallowed(String msg) {
            return new Response(false, msg);
        }
        
        private final boolean allowed;
        private final String msg;

        private Response(boolean allowed, String msg) {
            this.allowed = allowed;
            this.msg = msg;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getMsg() {
            return msg;
        }
    }

    public Response canProvisionLocation(Location provisioner);

    public Response canManageLocation(Location loc);
    
    public Response canManageEntity(Entity entity);
}

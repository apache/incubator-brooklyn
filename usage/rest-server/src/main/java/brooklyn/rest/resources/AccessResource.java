package brooklyn.rest.resources;

import javax.ws.rs.core.Response;

import brooklyn.management.internal.AccessManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.api.AccessApi;
import brooklyn.rest.domain.AccessSummary;
import brooklyn.rest.transform.AccessTransformer;

import com.google.common.annotations.Beta;

@Beta
public class AccessResource extends AbstractBrooklynRestResource implements AccessApi {

    @Override
    public AccessSummary get() {
        AccessManager accessManager = ((ManagementContextInternal) mgmt()).getAccessManager();
        return AccessTransformer.accessSummary(accessManager);
    }

    @Override
    public Response locationProvisioningAllowed(boolean allowed) {
        AccessManager accessManager = ((ManagementContextInternal) mgmt()).getAccessManager();
        accessManager.setLocationProvisioningAllowed(allowed);
        return Response.status(Response.Status.OK).build();
    }
}

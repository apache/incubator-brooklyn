package brooklyn.rest.transform;

import java.net.URI;

import brooklyn.management.internal.AccessManager;
import brooklyn.rest.domain.AccessSummary;

import com.google.common.collect.ImmutableMap;

/**
 * @author Adam Lowe
 */
public class AccessTransformer {

    public static AccessSummary accessSummary(AccessManager manager) {
        String selfUri = "/v1/access/";
        ImmutableMap<String, URI> links = ImmutableMap.of("self", URI.create(selfUri));

        return new AccessSummary(manager.isLocationProvisioningAllowed(), links);
    }
}

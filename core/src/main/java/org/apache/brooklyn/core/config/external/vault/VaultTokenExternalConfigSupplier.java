package org.apache.brooklyn.core.config.external.vault;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.text.Strings;

public class VaultTokenExternalConfigSupplier extends VaultExternalConfigSupplier {
    public VaultTokenExternalConfigSupplier(ManagementContext managementContext, String name, Map<String, String> config) {
        super(managementContext, name, config);
    }

    @Override
    protected String initAndLogIn(Map<String, String> config) {
        String tokenProperty = config.get("token");
        checkArgument(Strings.isNonBlank(tokenProperty), "property not set: token");
        return tokenProperty;
    }
}

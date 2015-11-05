package org.apache.brooklyn.core.config.external.vault;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.text.Strings;

import com.google.api.client.util.Lists;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;

public class VaultUserPassExternalConfigSupplier extends VaultExternalConfigSupplier {
    public VaultUserPassExternalConfigSupplier(ManagementContext managementContext, String name, Map<String, String> config) {
        super(managementContext, name, config);
    }

    @Override
    protected String initAndLogIn(Map<String, String> config) {
        List<String> errors = Lists.newArrayListWithCapacity(2);
        String username = config.get("username");
        if (Strings.isBlank(username)) errors.add("missing configuration 'username'");
        String password = config.get("password");
        if (Strings.isBlank(username)) errors.add("missing configuration 'password'");
        if (!errors.isEmpty()) {
            String message = String.format("Problem configuration Vault external config supplier '%s': %s",
                    name, Joiner.on(System.lineSeparator()).join(errors));
            throw new IllegalArgumentException(message);
        }

        String path = "v1/auth/userpass/login/" + username;
        ImmutableMap<String, String> requestData = ImmutableMap.of("password", password);
        ImmutableMap<String, String> headers = MINIMAL_HEADERS;
        JsonObject response = apiPost(path, headers, requestData);
        return response.getAsJsonObject("auth").get("client_token").getAsString();
    }
}

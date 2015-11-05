package org.apache.brooklyn.core.config.external.vault;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

public class VaultAppIdExternalConfigSupplier extends VaultExternalConfigSupplier {

    private static final Logger LOG = LoggerFactory.getLogger(VaultAppIdExternalConfigSupplier.class);

    protected VaultAppIdExternalConfigSupplier(ManagementContext managementContext, String name, Map<String, String> config) {
        super(managementContext, name, config);
    }

    protected String initAndLogIn(Map<String, String> config) {
        List<String> errors = Lists.newArrayListWithCapacity(1);
        String appId = config.get("appId");
        if (Strings.isBlank(appId)) errors.add("missing configuration 'appId'");
        if (!errors.isEmpty()) {
            String message = String.format("Problem configuration Vault external config supplier '%s': %s",
                    name, Joiner.on(System.lineSeparator()).join(errors));
            throw new IllegalArgumentException(message);
        }

        String userId = getUserId(config);

        LOG.info("Config supplier named {} using Vault at {} appID {} userID {} path {}", new Object[] {
                name, endpoint, appId, userId, path });

        String path = "v1/auth/app-id/login";
        ImmutableMap<String, String> requestData = ImmutableMap.of("app_id", appId, "user_id", userId);
        ImmutableMap<String, String> headers = MINIMAL_HEADERS;
        JsonObject response = apiPost(path, headers, requestData);
        return response.getAsJsonObject("auth").get("client_token").getAsString();
    }

    private String getUserId(Map<String, String> config) {
        String userId = config.get("userId");
        if (Strings.isBlank(userId))
            userId = getUserIdFromMacAddress();
        return userId;
    }

    private static String getUserIdFromMacAddress() {
        byte[] mac;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            mac = network.getHardwareAddress();
        } catch (Throwable t) {
            throw Exceptions.propagate(t);
        }
        StringBuilder sb = new StringBuilder();
        for (byte aMac : mac) {
            sb.append(String.format("%02x", aMac));
        }
        return sb.toString();
    }

}

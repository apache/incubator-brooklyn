package brooklyn.entity.monitoring.zabbix;

import javax.annotation.Nullable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;

public class ZabbixPollConfig<T> extends PollConfig<HttpToolResponse, T, ZabbixPollConfig<T>> {

    private String itemKey;

    public ZabbixPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        // Add onSuccess method to extract the last value of the item
        // FIXME Fix generics
        onSuccess((Function)HttpValueFunctions.chain(
                HttpValueFunctions.jsonContents(),
                new Function<JsonElement, JsonElement>() {
                    @Override
                    public JsonElement apply(@Nullable JsonElement input) {
                        Preconditions.checkNotNull(input, "JSON input");
                        return input.getAsJsonObject().get("result")
                                .getAsJsonArray().get(0)
                                .getAsJsonObject().get("lastvalue");
                    }
                },
                JsonFunctions.cast(getSensor().getType())));
    }

    public ZabbixPollConfig(ZabbixPollConfig<T> other) {
        super(other);
        this.itemKey = other.getItemKey();
    }

    public String getItemKey() {
        return itemKey;
    }

    public ZabbixPollConfig<T> itemKey(String val) {
        this.itemKey = val;
        return this;
    }

}

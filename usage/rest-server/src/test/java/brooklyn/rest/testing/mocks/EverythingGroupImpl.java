package brooklyn.rest.testing.mocks;

import brooklyn.entity.basic.DynamicGroupImpl;

import com.google.common.base.Predicates;

public class EverythingGroupImpl extends DynamicGroupImpl implements EverythingGroup {

    public EverythingGroupImpl() {
        super();
        setConfig(ENTITY_FILTER, Predicates.alwaysTrue());
    }

}

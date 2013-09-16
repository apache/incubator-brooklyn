package brooklyn.rest.transform;

import java.net.URI;
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.domain.EffectorSummary;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EffectorTransformer {

    public static EffectorSummary effectorSummary(EntityLocal entity, Effector<?> effector) {
        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();
        return new EffectorSummary(effector.getName(), effector.getReturnTypeName(),
                 ImmutableSet.copyOf(Iterables.transform(effector.getParameters(),
                new Function<ParameterType<?>, EffectorSummary.ParameterSummary>() {
                    @Override
                    public EffectorSummary.ParameterSummary apply(@Nullable ParameterType<?> parameterType) {
                        return parameterSummary(parameterType);
                    }
                })), effector.getDescription(), ImmutableMap.of(
                "self", URI.create(entityUri + "/effectors/" + effector.getName()),
                "entity", URI.create(entityUri),
                "application", URI.create(applicationUri)
        ));
    }

    public static EffectorSummary effectorSummaryForCatalog(Effector<?> effector) {
        Set<EffectorSummary.ParameterSummary> parameters = ImmutableSet.copyOf(Iterables.transform(effector.getParameters(),
                new Function<ParameterType<?>, EffectorSummary.ParameterSummary>() {
                    @Override
                    public EffectorSummary.ParameterSummary apply(ParameterType<?> parameterType) {
                        return parameterSummary(parameterType);
                    }
                }));
        return new EffectorSummary(effector.getName(),
                effector.getReturnTypeName(), parameters, effector.getDescription(), null);
    }
    
    protected static EffectorSummary.ParameterSummary parameterSummary(ParameterType<?> parameterType) {
        return new EffectorSummary.ParameterSummary(parameterType.getName(), parameterType.getParameterClassName(), parameterType.getDescription());
    }
}

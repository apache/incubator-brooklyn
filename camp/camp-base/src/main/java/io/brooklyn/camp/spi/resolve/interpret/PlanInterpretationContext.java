package io.brooklyn.camp.spi.resolve.interpret;

import io.brooklyn.camp.spi.resolve.PlanInterpreter;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class PlanInterpretationContext {

    private final Map<String,Object> originalDeploymentPlan;
    private final List<PlanInterpreter> interpreters;
    private final PlanInterpreter allInterpreter;

    public PlanInterpretationContext(Map<String,Object> originalDeploymentPlan, List<PlanInterpreter> interpreters) {
        super();
        this.originalDeploymentPlan = ImmutableMap.copyOf(originalDeploymentPlan);
        this.interpreters = ImmutableList.copyOf(interpreters);
        this.allInterpreter = new PlanInterpreter() {
            @Override
            public boolean isInterestedIn(PlanInterpretationNode node) {
                return true;
            }
            
            @Override
            public void applyYamlPrimitive(PlanInterpretationNode node) {
                for (PlanInterpreter i: PlanInterpretationContext.this.interpreters) {
                    if (node.isExcluded())
                        break;
                    if (i.isInterestedIn(node)) {
                        i.applyYamlPrimitive(node);
                    }
                }
            }

            @Override
            public boolean applyMapBefore(PlanInterpretationNode node, Map<Object, Object> mapIn) {
                boolean result = true;
                for (PlanInterpreter i: PlanInterpretationContext.this.interpreters) {
                    if (node.isExcluded())
                        break;
                    if (i.isInterestedIn(node)) {
                        boolean ri= i.applyMapBefore(node, mapIn);
                        result &= ri;
                    }
                }
                return result;
            }

            @Override
            public boolean applyMapEntry(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut, 
                                    PlanInterpretationNode key, PlanInterpretationNode value) {
                boolean result = true;
                for (PlanInterpreter i: PlanInterpretationContext.this.interpreters) { 
                    if (node.isExcluded())
                        break;
                    if (i.isInterestedIn(key)) {
                        boolean ri = i.applyMapEntry(node, mapIn, mapOut, key, value);
                        result &= ri;
                    }
                }
                return result;
            }

            @Override
            public void applyMapAfter(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut) {
                for (PlanInterpreter i: PlanInterpretationContext.this.interpreters) { 
                    if (node.isExcluded())
                        break;
                    if (i.isInterestedIn(node)) {
                        i.applyMapAfter(node, mapIn, mapOut);
                    }
                }
            }

            @Override
            public boolean applyListBefore(PlanInterpretationNode node, List<Object> listIn) {
                boolean result = true;
                for (PlanInterpreter i: PlanInterpretationContext.this.interpreters) { 
                    if (node.isExcluded())
                        break;
                    if (i.isInterestedIn(node)) {
                        boolean ri = i.applyListBefore(node, listIn);
                        result &= ri;
                    }
                }
                return result;
            }

            @Override
            public boolean applyListEntry(PlanInterpretationNode node, List<Object> listIn, List<Object> listOut, 
                                    PlanInterpretationNode value) {
                boolean result = true;
                for (PlanInterpreter i: PlanInterpretationContext.this.interpreters) { 
                    if (node.isExcluded())
                        break;
                    if (i.isInterestedIn(value)) {
                        boolean ri = i.applyListEntry(node, listIn, listOut, value);
                        result &= ri;
                    }
                }
                return result;
            }

            @Override
            public void applyListAfter(PlanInterpretationNode node, List<Object> listIn, List<Object> listOut) {
                for (PlanInterpreter i: PlanInterpretationContext.this.interpreters) { 
                    if (node.isExcluded())
                        break;
                    if (i.isInterestedIn(node)) {
                        i.applyListAfter(node, listIn, listOut);
                    }
                }
            }

        };
    }

    /** returns an interpreter which recurses through all interpreters */
    PlanInterpreter getAllInterpreter() {
        return allInterpreter;
    }

    public Map<String,Object> getOriginalDeploymentPlan() {
        return originalDeploymentPlan;
    }

    public List<PlanInterpreter> getInterpreters() {
        return interpreters;
    }

}

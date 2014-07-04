package io.brooklyn.camp.spi.pdp;

import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.spi.resolve.PlanInterpreter.PlanInterpreterAdapter;
import io.brooklyn.camp.spi.resolve.interpret.PlanInterpretationNode;
import io.brooklyn.util.yaml.Yamls;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;

@Test
public class DeploymentPlanToyInterpreterTest {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPlanToyInterpreterTest.class);
    
    /**
     * Allows testing:
     * 
     * $sample:foo becomes "bar"
     * $sample:caps:x capitalises the argument x
     * $sample:ignore as key causes key-value to be dropped (and value 0 for good measure)
     * $sample:reset causes containing map or list to be cleared at that point
     * $sample:remove as key causes argument as map to be dropped
     */
    public static class ToyInterpreter extends PlanInterpreterAdapter {
        
        @Override
        public boolean isInterestedIn(PlanInterpretationNode node) {
            return node.matchesPrefix("$sample:");
        }
        
        @Override
        public void applyYamlPrimitive(PlanInterpretationNode node) {
            if (node.matchesLiteral("$sample:foo")) node.setNewValue("bar").exclude();
            if (node.matchesPrefix("$sample:caps:")) {
                node.setNewValue(Strings.removeFromStart(node.getNewValue().toString(), "$sample:caps:").toUpperCase()).exclude();
            }
        }

        @Override
        public boolean applyMapBefore(PlanInterpretationNode node, Map<Object, Object> mapIn) {
            if (node.matchesLiteral("$sample:ignore"))
                // replace
                mapIn.put("$sample:ignore", 0);
            return super.applyMapBefore(node, mapIn);
        }
        
        @Override
        public boolean applyMapEntry(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut,
                                PlanInterpretationNode key, PlanInterpretationNode value) {
            if (key.matchesLiteral("$sample:ignore")) {
                Assert.assertEquals(value.getNewValue(), 0);
                return false;
            }
            if (key.matchesLiteral("$sample:reset")) {
                mapOut.clear();
                return false;
            }
            
            return super.applyMapEntry(node, mapIn, mapOut, key, value);
        }

        @SuppressWarnings("rawtypes")
        @Override
        public void applyMapAfter(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut) {
            if (mapOut.containsKey("$sample:remove")) {
                Map toRemove = (Map) mapOut.get("$sample:remove");
                for (Object vv: toRemove.keySet()) mapOut.remove(vv);
                mapOut.remove("$sample:remove");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void testToyInterpreter() {
        @SuppressWarnings("rawtypes")
        Map y1 = Yamls.getAs( Yamls.parseAll( Streams.reader(getClass().getResourceAsStream("yaml-sample-toy-interpreter.yaml"))), Map.class );
        log.info("pre-interpreter have: "+y1);
        
        BasicCampPlatform p = new BasicCampPlatform();
        p.pdp().addInterpreter(new ToyInterpreter());
        Map<String, Object> y2 = p.pdp().applyInterpreters(y1);
        log.info("interpreter gives: "+y2);
        
        Map<String, Object> y3 = Yamls.getAs( Yamls.parseAll( Streams.reader(getClass().getResourceAsStream("yaml-sample-toy-interpreter-result.yaml"))), Map.class );
        Assert.assertEquals(y2, y3);
    }

}

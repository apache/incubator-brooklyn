package brooklyn.entity.rebind.transformer;

import static org.testng.Assert.assertTrue;

import java.util.Collection;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.transformer.impl.XsltTransformer;

import com.google.common.collect.Iterables;

public class CompoundTransformerLoaderTest {

    @Test
    public void testLoadsTransformerFromYaml() throws Exception {
        String contents =
                "renameType:\n"+
                "  old_val: myoldname\n"+
                "  new_val: mynewname\n"+
                "renameClass:\n"+
                "  old_val: myoldname\n"+
                "  new_val: mynewname\n"+
                "renameField:\n"+
                "  class_name: myclassname\n"+
                "  old_val: myoldname\n"+
                "  new_val: mynewname\n"+
                "xslt:\n"+
                "  url: classpath://brooklyn/entity/rebind/transformer/renameType.xslt\n"+
                "  substitutions:\n"+
                "    old_val: myoldname\n"+
                "    new_val: mynewname\n"+
                "rawDataTransformer:\n"+
                "  type: "+MyRawDataTransformer.class.getName()+"\n";
        
        CompoundTransformer transformer = CompoundTransformerLoader.load(contents);
        Collection<RawDataTransformer> rawDataTransformers = transformer.getRawDataTransformers().get(BrooklynObjectType.ENTITY);
        assertTrue(Iterables.get(rawDataTransformers, 0) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 1) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 2) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 3) instanceof XsltTransformer);
        assertTrue(Iterables.get(rawDataTransformers, 4) instanceof MyRawDataTransformer);
    }
    
    public static class MyRawDataTransformer implements RawDataTransformer {
        @Override
        public String transform(String input) throws Exception {
            return input; // no-op
        }
    }
}

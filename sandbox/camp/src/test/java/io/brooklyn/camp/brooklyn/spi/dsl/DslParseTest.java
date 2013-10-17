package io.brooklyn.camp.brooklyn.spi.dsl;

import io.brooklyn.camp.brooklyn.spi.dsl.parse.DslParser;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.FunctionWithArgs;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.QuotedString;

import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.collect.Iterables;

@Test
public class DslParseTest {

    public void testParseString() {
        Assert.assertEquals(new DslParser("\"hello world\"").parse(), new QuotedString(JavaStringEscapes.wrapJavaString("hello world")));
    }

    public void testParseFunction() {
        Object fx = new DslParser("f(\"x\")").parse();
        fx = Iterables.getOnlyElement( (List<?>)fx );
        Assert.assertEquals( ((FunctionWithArgs)fx).getFunction(), "f" );
        Assert.assertEquals( ((FunctionWithArgs)fx).getArgs(), Arrays.asList(new QuotedString("\"x\"")) );
    }
    
    public void testParseFunctionChain() {
        Object fx = new DslParser("f(\"x\").g()").parse();
        Assert.assertTrue(((List<?>)fx).size() == 2, ""+fx);
        Object fx1 = ((List<?>)fx).get(0);
        Object fx2 = ((List<?>)fx).get(1);
        Assert.assertEquals( ((FunctionWithArgs)fx1).getFunction(), "f" );
        Assert.assertEquals( ((FunctionWithArgs)fx1).getArgs(), Arrays.asList(new QuotedString("\"x\"")) );
        Assert.assertEquals( ((FunctionWithArgs)fx2).getFunction(), "g" );
        Assert.assertTrue( ((FunctionWithArgs)fx2).getArgs().isEmpty() );
    }
    

}

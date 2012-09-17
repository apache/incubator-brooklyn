package brooklyn.util.text;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.text.StringEscapes.BashStringEscapes;

public class StringEscapesTest {

    @Test
    public void testEscapeSql() {
        Assert.assertEquals(StringEscapes.escapeSql("I've never been to Brooklyn"), "I''ve never been to Brooklyn");
    }

    
	@Test
	public void testBashEscaping() {
		Assert.assertEquals(
	        BashStringEscapes.doubleQuoteLiteralsForBash("-Dname=Bob Johnson", "-Dnet.worth=$100"),
			"\"-Dname=Bob Johnson\" \"-Dnet.worth=\\$100\"");
	}

	@Test
	public void testBashEscapable() {
		Assert.assertTrue(BashStringEscapes.isValidForDoubleQuotingInBash("Bob Johnson"));
		Assert.assertFalse(BashStringEscapes.isValidForDoubleQuotingInBash("\""));
		Assert.assertTrue(BashStringEscapes.isValidForDoubleQuotingInBash("\\\""));
	}	
    
    @Test
    public void testBashEscapableAmpersand() {
        Assert.assertTrue(BashStringEscapes.isValidForDoubleQuotingInBash("\\&"));
        Assert.assertFalse(BashStringEscapes.isValidForDoubleQuotingInBash("Marks & Spencer"));
        Assert.assertTrue(BashStringEscapes.isValidForDoubleQuotingInBash("Marks \\& Spencer"));
        Assert.assertFalse(BashStringEscapes.isValidForDoubleQuotingInBash("Marks \\\\& Spencer"));
    }
    
}

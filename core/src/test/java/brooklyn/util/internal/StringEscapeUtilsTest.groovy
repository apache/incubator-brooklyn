package brooklyn.util.internal;

import org.testng.Assert;
import org.testng.annotations.Test;

public class StringEscapeUtilsTest {

	@Test
	public void testBashEscaping() {
		Assert.assertEquals(
			StringEscapeUtils.doubleQuoteLiteralsForBash("-Dname=Bob Johnson", "-Dnet.worth=\$100"),
			"\"-Dname=Bob Johnson\" \"-Dnet.worth=\\\$100\"");
	}

	@Test
	public void testBashEscapable() {
		Assert.assertTrue StringEscapeUtils.isValidForDoubleQuotingInBash("Bob Johnson")
		Assert.assertFalse StringEscapeUtils.isValidForDoubleQuotingInBash("\"")
		Assert.assertTrue StringEscapeUtils.isValidForDoubleQuotingInBash("\\\"")
	}	
    
    @Test
    public void testBashEscapableAmpersand() {
        Assert.assertTrue StringEscapeUtils.isValidForDoubleQuotingInBash("\\&")
        Assert.assertFalse StringEscapeUtils.isValidForDoubleQuotingInBash("Marks & Spencer")
        Assert.assertTrue StringEscapeUtils.isValidForDoubleQuotingInBash("Marks \\& Spencer")
        Assert.assertFalse StringEscapeUtils.isValidForDoubleQuotingInBash("Marks \\\\& Spencer")
    }

    @Test
    public void testEscapeSql() {
        Assert.assertEquals(StringEscapeUtils.escapeSql("I've never been to Brooklyn"), "I''ve never been to Brooklyn");
    }
    
}

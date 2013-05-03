package brooklyn.util;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.Lists;

public class CommandLineUtilTest {

	@Test
	public void testGetCommandReturnsDefaultIfNotPresent() throws Exception {
		List<String> args = Lists.newArrayList("k1", "v1");
		String result = CommandLineUtil.getCommandLineOption(args, "notthere", "mydefault");
		assertEquals(result, "mydefault");
		assertEquals(args, Arrays.asList("k1", "v1"));
	}
	
	@Test
	public void testGetCommandReturnsParamAndRemovesIt() throws Exception {
	    List<String> args = Lists.newArrayList("k1", "v1");
		String result = CommandLineUtil.getCommandLineOption(args, "k1");
		assertEquals(result, "v1");
		assertEquals(args, Arrays.asList());
	}
	
	@Test
	public void testGetCommandReturnsParamAndRemovesItButLeavesOtherVals() throws Exception {
	    List<String> args = Lists.newArrayList("k1", "v1", "k2", "v2");
		String result = CommandLineUtil.getCommandLineOption(args, "k1");
		assertEquals(result, "v1");
		assertEquals(args, Arrays.asList("k2", "v2"));
	}
	
	@Test
	public void testGetCommandReturnsParamAndRemovesItButLeavesOtherValsWhenDuplicateVals() throws Exception {
	    List<String> args = Lists.newArrayList("k1", "vdup", "k2", "v2", "k3", "vdup");
		String result = CommandLineUtil.getCommandLineOption(args, "k3");
		assertEquals(result, "vdup");
		assertEquals(args, Arrays.asList("k1", "vdup", "k2", "v2"));
	}
}

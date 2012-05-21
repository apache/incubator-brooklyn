package brooklyn.rest.commands;

import com.yammer.dropwizard.testing.ResourceTest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import org.apache.commons.cli.GnuParser;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(singleThreaded = true)
public abstract class BrooklynCommandTest extends ResourceTest {

  private ByteArrayOutputStream outBytes;
  private PrintStream out;

  private ByteArrayOutputStream errBytes;
  private PrintStream err;

  @BeforeClass
  @Override
  public void setUpJersey() throws Exception {
    super.setUpJersey();
  }

  @BeforeMethod
  public void setUp() {
    outBytes = new ByteArrayOutputStream();
    out = new PrintStream(outBytes);

    errBytes = new ByteArrayOutputStream();
    err = new PrintStream(errBytes);
  }

  @AfterClass
  @Override
  public void tearDownJersey() throws Exception {
    super.tearDownJersey();
  }

  protected void runCommandWithArgs(Class<? extends BrooklynCommand> clazz, String... args) throws Exception {
    BrooklynCommand cmd = clazz.newInstance();
    cmd.runAsATest(out, err, client(), new GnuParser().parse(cmd.getOptions(), args));
  }

  protected String standardOut() {
    return outBytes.toString();
  }

  protected String standardErr() {
    return errBytes.toString();
  }

  protected String createTemporaryFileWithContent(String suffix, String content)
      throws IOException {
    File temporaryFile = File.createTempFile("brooklyn-rest", suffix);
    Writer writer = null;

    try {
      writer = new FileWriter(temporaryFile);
      writer.write(content);
      return temporaryFile.getAbsolutePath();

    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }
}

package brooklyn.rest;

public class ExecutorConfiguration {

  private int corePoolSize = 2;

  private int maximumPoolSize = 16;

  private int keepAliveTimeInSeconds = 120;

  public int getCorePoolSize() {
    return corePoolSize;
  }

  public int getMaximumPoolSize() {
    return maximumPoolSize;
  }

  public int getKeepAliveTimeInSeconds() {
    return keepAliveTimeInSeconds;
  }
}

package brooklyn.entity.basic.lifecycle;

public class MyEntityApp {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Properties: "+System.getProperties());
        Thread.sleep(60*60*1000);
    }
}

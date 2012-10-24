package brooklyn.entity.java;

public class ExampleVanillaMainCpuHungry {
    private static final int MAX_TIME_MILLIS = 100*1000;
    private static final int CALCULATIONS_PER_CYCLE = 100000;
    private static final int SLEEP_PER_CYCLE_MILLIS = 1;
    
    public static void main(String[] args) throws Exception {
        System.out.println("In ExampleVanillaMainCpuHungry.main");
        long startTime = System.currentTimeMillis();
        long count = 0;
        double total = 0;
        do {
            for (int i = 0; i < CALCULATIONS_PER_CYCLE; i++) {
                total += Math.sqrt(Math.random());
                count++;
            }
            Thread.sleep(SLEEP_PER_CYCLE_MILLIS);
        } while ((System.currentTimeMillis() - startTime) < MAX_TIME_MILLIS);
        
        System.out.println("Did "+count+" random square roots, took "+(System.currentTimeMillis()-startTime)+"ms; total = "+total);
    }
}

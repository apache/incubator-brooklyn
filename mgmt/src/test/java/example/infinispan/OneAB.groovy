package example.infinispan;

public class OneAB {

    public static void main(String[] args) {
        new OneAB().test();
    }
    
    Throwable error = null;
    public void test() {
        def t1 = new Thread({ try { OneA.main() } catch (Throwable t) { error = t; error.printStackTrace(); }});
        def t2 = new Thread({ try { OneB.main() } catch (Throwable t) { error = t; error.printStackTrace(); }});
        t1.start();
        Thread.sleep(5000+(int)(3000*Math.random()));
        t2.start();
        
        t1.join();
        t2.join();
        
        if (error) {
            error.printStackTrace()
            throw error
        }
    }
}

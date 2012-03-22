package org.infinispan.examples.tutorial.clustered;

public class Node01 {

    public static void main(String[] args) {
        new Thread( { Node0.main() } ).start();
        Thread.sleep(1000);
        new Thread( { Node1.main() } ).start();
    }
    
}

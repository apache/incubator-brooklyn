Instructions for running examples
=================================

The commands below assume that the `brooklyn` script is already on your $PATH, and you are in the `examples` directory:

    cd simple-nosql-cluster
    export BROOKLYN_CLASSPATH=$(pwd)/target/classes
    
    # Launches a Redis cluster on AWS EC2
    brooklyn -v launch --app brooklyn.demo.SimpleRedisCluster --location aws-ec2:eu-west-1
    
    # Launches a Cassandra cluster on AWS EC2
    brooklyn -v launch --app brooklyn.demo.SimpleCassandraCluster --location aws-ec2:eu-west-1
    
    # Launches a CouchDB cluster on AWS EC2
    brooklyn -v launch --app brooklyn.demo.SimpleCouchDBCluster --location aws-ec2:eu-west-1
    
    # Launches a CumulusRDF application backed by a cassandra cluster on AWS EC2
    brooklyn -v launch --app brooklyn.demo.CumulusRDFApplication --location aws-ec2:eu-west-1

--------

For more information, please visit the (http://brooklyncentral.github.com/use/examples/nosql/)[NoSQL examples page]

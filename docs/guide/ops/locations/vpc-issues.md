
### AWS VPC issues which may affect users with older AWS accounts

AWS now has different default behaviour depending on the age of your AWS account and whether you used the target region before, or during, 2013.
In this case VM provisioning may fail with an error like:

{% highlight text %}

Detected that your EC2 account is a legacy 'classic' account, but the recommended instance type requires VPC. 
You can specify the 'eu-central-1' region to avoid this problem, or you can specify a classic-compatible instance type, 
or you can specify a subnet to use with 'networkName' 
taking care that the subnet auto-assigns public IP's and allows ingress on all ports, 
as Brooklyn does not currently configure security groups for non-default VPC's; 
or setting up Brooklyn to be in the subnet or have a jump host or other subnet access configuration). 
For more information on VPC vs classic see http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-vpc.html.

{% endhighlight %}

Specifically, there are issues with the certain AMIs and instance types.  If these are specified or a recommended 
by brooklyn then you may see the above error. There are a few options for fixing this:

- specify a different region which does not support EC2-classic.  
  You can check this on the AWS console under "Supported Platforms.
  Frankfurt (eu-central-1) is guaranteed to be VPC only.
  
- specify an instance type that is compatible with ec2-classic.  
  Instance types C4, M4, T2 are only supported in VPC so should not be used.
  This is described [here](index.html#vm-creation)
    
- create a subnet to use with the instance. Ensure that the subnet is set to auto-assign public IPs
  and allows ingress on all ports.  Brooklyn cannot currently do this for you.
  Use the networkName parameter to specify this value in your blueprint.
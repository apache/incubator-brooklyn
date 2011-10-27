package brooklyn.extras.openshift

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.test.TestUtils

/**
 * example app showing how to start an openshift java war
 *  
 * if this doesn't start, we may have too many apps, delete some using:
 * rhc-user-info -l openshift@cloudsoftcorp.com -p 0penshift 
 * rhc-ctl-app -l openshift@cloudsoftcorp.com -p 0penshift -a Brooklyn4f9b7369 -c destroy
 * (or online at the openshift express portal)
 * 
 * @author alex
 *
 */
class OpenshiftExpressJavaClusterOnlyExample extends AbstractApplication {

//    here we assume, in addition to assumptions in the integration test,
//    that ssh keys have been set up as follows (for the openshift account)
//    
//    % cat ~/.ssh/libra_id_rsa
//    -----BEGIN RSA PRIVATE KEY-----
//    MIIEpAIBAAKCAQEA8VrGR0439k6TiNz/mby+sDV4KBp9yv4s+pv/M1gknxGfMNxs
//    Izi6bdrGPtCS4NKrTImGqeK0xUFa98WhVS0gHbdX8ebi+RxfOYM5w7NOLlzVzOrE
//    Th+PIIHepOFTDCGi8qlly2v6eYq2jYLVOyVakFAp29/qG9QhRFGQvdknXq2JGP0+
//    EB2kBeIJY4V9AesJk9dTgTrJALxs1p32tU44+IP4Tbfsn73LiHuaRZCI/B03Ug4q
//    lRyhvXG8sl9VAaGCVTWU9qn4zZWzrGG6XUWss4UG7v5isiRUcz3onizD9sgEfgo+
//    XoitCzTX30nQZYNVt1gbGGT+Pn+q49hM04jUHQIBIwKCAQEA1cVtyiC1OT5IKMO9
//    0U9nERlqbKm/363tUv8yfe773VjAMpdYdve7ENe25y3D67NHQ8pEEtAcdDKSZm03
//    H45eRjSs+tPQWPvfUDJmXOCkVPMjxCBkujk1oHMHBxC3RUJdIBJheajH5/6EbrW1
//    jChmKAx82LBho56hH0Dt6frZukA2Imsy6d8f0BVWP0ZjrT9qkavmPH3SP1YzWR+c
//    HvprlmmGNlVtXNuCjkJcowSB4zHp4xGeunS52MTnltRF9hOzDUT/Fm3XKF5UhRRe
//    up+rxDThyGv5c5OB0SsxLRmWrKbY11i7s0SZPo/yRtEGoXs7Zb6HCgUxaYeGyEmy
//    SEXiywKBgQD459PvECwEEi0UnI4DyMzXUgG+kPYXhd9S122RAJUjbafHA+cI1J42
//    R4dq4SXb5JsfkGk+ALkgOuab3Dp+7tr9B0y3XM0YDXr8ty5gM6o/Cn59AgL2Jmil
//    wyMvKAimK1awBaRENDec5jMRpOk/XsItZ+wGGiEYN/RSwy/kx/bSsQKBgQD4O9mR
//    Y0AIa97uPXZ0CiktjNmyBs9pQ97gBeutLqacEpaZwiYd/zAB2iUhpBtDvCi02gzm
//    yiHIdQenwH9+06gFeLv/qdR6N7A2f/tSTqUXFdMXZYnGhR1mcV2SdMUpWxgNEab0
//    sn975qFRl9eqkoBY3Iy2Vgx8sG4zkRtk88c7LQKBgQDqrrHhZwTt5UByhPrtoBGX
//    0PpUl05t80BGyx4poXasYBM3/F19WsEOmzaJV/B+3ttm5z6oLJFKRinjaT53rgGs
//    vb1cbXDqyuGscjMKP1dgApSEfj1Oe/xEhMlm+dxE5wiXVcbD9rgXlzd3CTOwxw7a
//    WqtWNeSwb0VymsbBw9oXGwKBgQDqDItOkMdYZbTvQUPMfpvafXzMbNIwDMrTOMhE
//    M0yhyGIb8ZGnQRABvw0RFxJkb5QYO03+LEu9AKDRXboJ3YEpuvpmFSdr610AL4aP
//    brjp4WChB/buYEBKpWbai1rPOKGl7BJxr5zFRypi35908IekPak+M9/jR0NVMQsz
//    TD+A7wKBgQDTzLMfmTAZeHzO9Yp3A8lE3e9vTUVA8f/55B1rZCwEtck9PreGpjfT
//    D3Wf0P3bJAyBz5f6vRoBqxVu6BdQkpPUdR6RcZYXQ0/ioCCsfFZchmr/tmzVkrWD
//    t7l0teURvg/2eJBOqWo7DHQPbsfpICy1mSqbY2+zYPNUdQImBtVOeg==
//    -----END RSA PRIVATE KEY-----
//    % cat /Users/alex/.ssh/libra_id_rsa.pub
//    ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA8VrGR0439k6TiNz/mby+sDV4KBp9yv4s+pv/M1gknxGfMNxsIzi6bdrGPtCS4NKrTImGqeK0xUFa98WhVS0gHbdX8ebi+RxfOYM5w7NOLlzVzOrETh+PIIHepOFTDCGi8qlly2v6eYq2jYLVOyVakFAp29/qG9QhRFGQvdknXq2JGP0+EB2kBeIJY4V9AesJk9dTgTrJALxs1p32tU44+IP4Tbfsn73LiHuaRZCI/B03Ug4qlRyhvXG8sl9VAaGCVTWU9qn4zZWzrGG6XUWss4UG7v5isiRUcz3onizD9sgEfgo+XoitCzTX30nQZYNVt1gbGGT+Pn+q49hM04jUHQ== alex@almac.rocklynn.cognetics.org
    
    private static final Logger log = LoggerFactory.getLogger(OpenshiftExpressJavaClusterOnlyExample.class)
    
    File warFile = TestUtils.getResource("hello-world.war", getClass().getClassLoader())
                
    OpenshiftExpressJavaWebAppCluster openshift = 
      new OpenshiftExpressJavaWebAppCluster(this, war: warFile.getAbsolutePath());
    
    // TODO a richer example which starts Openshift alongside JBosses in EC2 with geoscaling
    // TODO (shouldn't use the tomcat-branded hello world for this :)
      
    // ---- the code above is your app descriptor; code below runs it ----
      
    OpenshiftLocation loc = new OpenshiftLocation(
          username: OpenshiftExpressAccessIntegrationTest.TEST_USER,
          password: OpenshiftExpressAccessIntegrationTest.TEST_PASSWORD)
      
    public static void main(String[] args) {
        def app = new OpenshiftExpressJavaClusterOnlyExample();
        
        app.start([app.loc]);
        
        log.info "should now be able to visit site (for 60s): {}", app.openshift.getWebAppAddress()
        //should now be able to visit (assert?)
        
        Thread.sleep(60*1000)

        //and kill
        log.info "now cleaning up that app: {}", app.openshift.getWebAppAddress()
        app.stop()
        
        log.info "finished, should terminate shortly"
    }
}

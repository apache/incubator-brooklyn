package brooklyn.demo;

import java.io.IOException;

import org.marre.SmsSender;
import org.marre.sms.SmsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sms {

    private static final Logger LOG = LoggerFactory.getLogger(Sms.class);

    private final String username;
    private final String password;
    private final String apiid;
    private final String sender;

    public Sms(String username, String password, String apiid) {
        this.username = username;
        this.password = password;
        this.apiid = apiid;
        this.sender = null;
    }
    
    /**
     * 
     * @param receiver International number to reciever without leading "+"
     * @param msg      The message that you want to send
     * @throws IOException 
     * @throws SmsException 
     */
    public void sendSms(String receiver, String msg) throws SmsException, IOException {
        // Send SMS with clickatell
        SmsSender smsSender = SmsSender.getClickatellSender(username, password, apiid);

        smsSender.connect();
        try {
            String msgid = smsSender.sendTextSms(msg, receiver, sender);
            LOG.debug("Sent SMS via {}@clickatell to {}, msg {}; id {}", new Object[] {username, receiver, msg, msgid});
        } finally {
            smsSender.disconnect();
        }
    }
}

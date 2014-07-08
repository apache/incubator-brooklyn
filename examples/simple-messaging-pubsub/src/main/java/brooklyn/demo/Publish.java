/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.demo;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.configuration.ClientProperties;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/** Publishes messages to a queue on a Qpid broker at a given URL. */
public class Publish {
    public static final String QUEUE = "'amq.direct'/'testQueue'; { node: { type: queue } }";
        
    public static void main(String...argv) throws Exception {
        Preconditions.checkElementIndex(0, argv.length, "Must specify broker URL");
        String url = argv[0];
        
        // Set Qpid client properties
        System.setProperty(ClientProperties.AMQP_VERSION, "0-10");
        System.setProperty(ClientProperties.DEST_SYNTAX, "ADDR");

        // Connect to the broker
        AMQConnectionFactory factory = new AMQConnectionFactory(url);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        try {
            // Create a producer for the queue
	        Queue destination = session.createQueue(QUEUE);
	        MessageProducer messageProducer = session.createProducer(destination);

	        // Send 100 messages
	        for (int n = 0; n < 100; n++) {
	            String body = String.format("test message %03d", n+1);
	            TextMessage message = session.createTextMessage(body);
	            messageProducer.send(message);
	            System.out.printf("Sent message %s\n", body);
	        }
        } catch (Exception e) {
            System.err.printf("Error while sending - %s\n", e.getMessage());
            System.err.printf("Cause: %s\n", Throwables.getStackTraceAsString(e));
        } finally {
            session.close();
            connection.close();
        }
    }
}

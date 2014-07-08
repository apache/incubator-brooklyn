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
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.configuration.ClientProperties;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/** Receives messages from a queue on a Qpid broker at a given URL. */
public class Subscribe {
    public static final String QUEUE = "'amq.direct'/'testQueue'; { node: { type: queue } }";
    private static final long MESSAGE_TIMEOUT_MILLIS = 15000L;
    private static final int MESSAGE_COUNT = 100;
    
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

        System.out.printf("Waiting up to %s milliseconds to receive %s messages\n", MESSAGE_TIMEOUT_MILLIS, MESSAGE_COUNT);
        try {
            // Create a producer for the queue
            Queue destination = session.createQueue(QUEUE);
            MessageConsumer messageConsumer = session.createConsumer(destination);

            // Try and receive 100 messages
            for (int n = 0; n < MESSAGE_COUNT; n++) {
                TextMessage msg = (TextMessage) messageConsumer.receive(MESSAGE_TIMEOUT_MILLIS);
                if (msg == null) {
                    System.out.printf("No message received in %s milliseconds, exiting", MESSAGE_TIMEOUT_MILLIS);
                    break;
                }
                System.out.printf("Got message %d: '%s'\n", n+1, msg.getText());
            }
        } catch (Exception e) {
            System.err.printf("Error while receiving - %s\n", e.getMessage());
            System.err.printf("Cause: %s\n", Throwables.getStackTraceAsString(e));
        } finally {
            session.close();
            connection.close();
        }
    }
}

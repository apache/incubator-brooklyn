--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--  http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
--
create database visitors;
use visitors;
create user 'brooklyn' identified by 'br00k11n';
grant usage on *.* to 'brooklyn'@'%' identified by 'br00k11n';
# ''@localhost is sometimes set up, overriding brooklyn@'%', so do a second explicit grant
grant usage on *.* to 'brooklyn'@'localhost' identified by 'br00k11n';
grant all privileges on visitors.* to 'brooklyn'@'%';
flush privileges;

CREATE TABLE MESSAGES (
        id INT NOT NULL AUTO_INCREMENT,
        NAME VARCHAR(30) NOT NULL,
        MESSAGE VARCHAR(400) NOT NULL,
        PRIMARY KEY (ID)
    );

INSERT INTO MESSAGES values (default, 'Isaac Asimov', 'I grew up in Brooklyn' );

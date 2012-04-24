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

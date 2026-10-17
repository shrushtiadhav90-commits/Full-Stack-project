package com.nexus.quiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Entry point for the Nexus Quiz Portal backend.
 *
 * <p>TimeZone.setDefault(UTC) is called as the very first statement before
 * Spring context initialisation so that every date/time operation — including
 * Hibernate, Jackson, and any java.util.Date usage — defaults to UTC.
 * The JVM is also started with {@code -Duser.timezone=UTC} via the Maven
 * plugin configuration in pom.xml for belt-and-suspenders coverage.
 */
@SpringBootApplication
@EnableScheduling
public class NexusQuizApplication {

    public static void main(String[] args) {
        // Must be the FIRST line – sets the JVM default timezone to UTC before
        // any Spring bean or Hibernate initialisation can read the system clock.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        SpringApplication.run(NexusQuizApplication.class, args);
    }
}

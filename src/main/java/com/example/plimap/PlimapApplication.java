package com.example.plimap;

import com.example.plimap.global.monitoring.StartupMonitoringListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlimapApplication {

    public static void main(String[] args) {
        long startedAtNanos = System.nanoTime();
        SpringApplication application = new SpringApplication(PlimapApplication.class);
        application.addListeners(new StartupMonitoringListener(startedAtNanos));
        application.run(args);
    }

}

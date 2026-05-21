package id.ac.ui.cs.advprog.biddingcommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BiddingCommandServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiddingCommandServiceApplication.class, args);
    }
}

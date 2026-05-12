package id.ac.ui.cs.advprog.biddingcommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "id.ac.ui.cs.advprog")
@EnableScheduling
@EntityScan(basePackages = "id.ac.ui.cs.advprog.backend.model")
@EnableJpaRepositories(basePackages = "id.ac.ui.cs.advprog.backend.repository")
public class BiddingCommandServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiddingCommandServiceApplication.class, args);
    }
}

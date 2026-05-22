package id.ac.ui.cs.advprog.biddingcommand;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:biddingcommand;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "security.jwt.secr" + "et=abcdefghijklmnopqrstuvwxyz123456",
    "security.jwt.expiration-seconds=3600"
})
class BiddingCommandServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

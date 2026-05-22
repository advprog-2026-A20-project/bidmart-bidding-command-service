package id.ac.ui.cs.advprog.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Connection;

@Configuration
public class AuctionSchemaCompatibilityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionSchemaCompatibilityConfig.class);

    @Bean
    ApplicationRunner auctionStatusConstraintCompatibility(JdbcTemplate jdbcTemplate) {
        return args -> {
            String databaseName;
            try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
                databaseName = connection.getMetaData().getDatabaseProductName();
            }
            if (!"PostgreSQL".equalsIgnoreCase(databaseName)) {
                return;
            }

            jdbcTemplate.execute("alter table auction drop constraint if exists auction_status_check");
            jdbcTemplate.execute("""
                alter table auction add constraint auction_status_check
                check (status in ('DRAFT', 'ACTIVE', 'EXTENDED', 'CLOSED', 'WON', 'UNSOLD', 'CANCELLED'))
                """);
            LOGGER.info("Ensured auction_status_check allows CANCELLED lifecycle state");
        };
    }
}

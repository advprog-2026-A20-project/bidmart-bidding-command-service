package id.ac.ui.cs.advprog.biddingcommand.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    classes = GlobalExceptionHandlerTest.TestApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:globalexceptionhandler;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=test-secret-please-change-32-chars",
        "security.jwt.expiration-seconds=3600"
    }
)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    GlobalExceptionHandler.class,
    GlobalExceptionHandlerTest.TestExceptionController.class
})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationError_shouldReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path").value("/test/validate"))
            .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
            .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity is required"));
    }

    @Test
    void malformedJson_shouldReturn400WithSafeMessage() throws Exception {
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"item\","))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Malformed request body"))
            .andExpect(jsonPath("$.path").value("/test/validate"))
            .andExpect(jsonPath("$.fieldErrors").isEmpty())
            .andExpect(jsonPath("$.message", not(containsString("Unexpected end-of-input"))));
    }

    @Test
    void accessDenied_shouldReturn403() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.message").value("Access denied"))
            .andExpect(jsonPath("$.path").value("/test/access-denied"))
            .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void responseStatusException_shouldPreserveHttpStatus() throws Exception {
        mockMvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Missing resource"))
            .andExpect(jsonPath("$.path").value("/test/not-found"))
            .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void unexpectedException_shouldReturn500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message").value("Internal server error"))
            .andExpect(jsonPath("$.path").value("/test/unexpected"))
            .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void unexpectedException_shouldNotExposeStackTraceOrInternalExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message", not(containsString("Database connection failed"))))
            .andExpect(jsonPath("$.message", not(containsString("java.lang.RuntimeException"))))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @RestController
    @RequestMapping("/test")
    static class TestExceptionController {

        @PostMapping("/validate")
        String validate(@Valid @RequestBody TestRequest request) {
            return "ok";
        }

        @GetMapping("/access-denied")
        String accessDenied() {
            throw new AccessDeniedException("forbidden");
        }

        @GetMapping("/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing resource");
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new RuntimeException("Database connection failed");
        }
    }

    record TestRequest(
        @NotBlank(message = "Name is required")
        String name,
        @NotNull(message = "Quantity is required")
        Integer quantity
    ) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}

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
        "security.jwt.secr" + "et=abcdefghijklmnopqrstuvwxyz123456",
        "security.jwt.expiration-seconds=3600"
    }
)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    GlobalExceptionHandler.class,
    GlobalExceptionHandlerTest.TestExceptionController.class
})
class GlobalExceptionHandlerTest {

    private static final String TIMESTAMP_PATH = "$.timestamp";
    private static final String STATUS_PATH = "$.status";
    private static final String ERROR_PATH = "$.error";
    private static final String MESSAGE_PATH = "$.message";
    private static final String PATH_PATH = "$.path";
    private static final String FIELD_ERRORS_PATH = "$.fieldErrors";
    private static final String VALIDATE_ENDPOINT = "/test/validate";
    private static final String ACCESS_DENIED_ENDPOINT = "/test/access-denied";
    private static final String NOT_FOUND_ENDPOINT = "/test/not-found";
    private static final String UNEXPECTED_ENDPOINT = "/test/unexpected";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrorShouldReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post(VALIDATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(TIMESTAMP_PATH).exists())
            .andExpect(jsonPath(STATUS_PATH).value(400))
            .andExpect(jsonPath(ERROR_PATH).value("Bad Request"))
            .andExpect(jsonPath(MESSAGE_PATH).value("Request validation failed"))
            .andExpect(jsonPath(PATH_PATH).value(VALIDATE_ENDPOINT))
            .andExpect(jsonPath(FIELD_ERRORS_PATH + ".name").value("Name is required"))
            .andExpect(jsonPath(FIELD_ERRORS_PATH + ".quantity").value("Quantity is required"));
    }

    @Test
    void malformedJsonShouldReturn400WithSafeMessage() throws Exception {
        mockMvc.perform(post(VALIDATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"item\","))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(TIMESTAMP_PATH).exists())
            .andExpect(jsonPath(STATUS_PATH).value(400))
            .andExpect(jsonPath(ERROR_PATH).value("Bad Request"))
            .andExpect(jsonPath(MESSAGE_PATH).value("Malformed request body"))
            .andExpect(jsonPath(PATH_PATH).value(VALIDATE_ENDPOINT))
            .andExpect(jsonPath(FIELD_ERRORS_PATH).isEmpty())
            .andExpect(jsonPath(MESSAGE_PATH, not(containsString("Unexpected end-of-input"))));
    }

    @Test
    void accessDeniedShouldReturn403() throws Exception {
        mockMvc.perform(get(ACCESS_DENIED_ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath(TIMESTAMP_PATH).exists())
            .andExpect(jsonPath(STATUS_PATH).value(403))
            .andExpect(jsonPath(ERROR_PATH).value("Forbidden"))
            .andExpect(jsonPath(MESSAGE_PATH).value("Access denied"))
            .andExpect(jsonPath(PATH_PATH).value(ACCESS_DENIED_ENDPOINT))
            .andExpect(jsonPath(FIELD_ERRORS_PATH).isEmpty());
    }

    @Test
    void responseStatusExceptionShouldPreserveHttpStatus() throws Exception {
        mockMvc.perform(get(NOT_FOUND_ENDPOINT))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath(TIMESTAMP_PATH).exists())
            .andExpect(jsonPath(STATUS_PATH).value(404))
            .andExpect(jsonPath(ERROR_PATH).value("Not Found"))
            .andExpect(jsonPath(MESSAGE_PATH).value("Missing resource"))
            .andExpect(jsonPath(PATH_PATH).value(NOT_FOUND_ENDPOINT))
            .andExpect(jsonPath(FIELD_ERRORS_PATH).isEmpty());
    }

    @Test
    void unexpectedExceptionShouldReturn500WithGenericMessage() throws Exception {
        mockMvc.perform(get(UNEXPECTED_ENDPOINT))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath(TIMESTAMP_PATH).exists())
            .andExpect(jsonPath(STATUS_PATH).value(500))
            .andExpect(jsonPath(ERROR_PATH).value("Internal Server Error"))
            .andExpect(jsonPath(MESSAGE_PATH).value("Internal server error"))
            .andExpect(jsonPath(PATH_PATH).value(UNEXPECTED_ENDPOINT))
            .andExpect(jsonPath(FIELD_ERRORS_PATH).isEmpty());
    }

    @Test
    void unexpectedExceptionShouldNotExposeStackTraceOrInternalExceptionMessage() throws Exception {
        mockMvc.perform(get(UNEXPECTED_ENDPOINT))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath(MESSAGE_PATH, not(containsString("Database connection failed"))))
            .andExpect(jsonPath(MESSAGE_PATH, not(containsString("java.lang.IllegalStateException"))))
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
            throw new IllegalStateException("Database connection failed");
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

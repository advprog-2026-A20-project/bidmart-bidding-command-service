package id.ac.ui.cs.advprog.backend.security;

import id.ac.ui.cs.advprog.backend.model.Role;
import java.util.UUID;

public record AuthenticatedUser(
    UUID id,
    String email,
    Role role
) {
}

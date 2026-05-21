package id.ac.ui.cs.advprog.biddingcommand.security;

import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import java.util.UUID;

public record AuthenticatedUser(
    UUID id,
    String email,
    Role role
) {
}

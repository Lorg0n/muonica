package io.muonica.demo.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.muonica.core.annotation.MuonicaDocumentation;
import io.muonica.core.annotation.MuonicaGroup;
import io.muonica.core.annotation.MuonicaOperation;
import io.muonica.core.annotation.MuonicaResponse;
import io.muonica.core.annotation.MuonicaSecurityRequirement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
@MuonicaGroup(name = "Users", description = "Create, read and search demo users.")
@MuonicaDocumentation(file = "classpath:/muonica/users/index.md")
class UserController {
    @GetMapping("/{id}")
    @MuonicaOperation(summary = "Get a user", description = "Returns a user by its numeric identifier.")
    @MuonicaDocumentation(file = "classpath:/muonica/users/get-user.md")
    @MuonicaResponse(status = 404, description = "User was not found", body = ErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    UserResponse getUser(@PathVariable long id) {
        return new UserResponse(id, "Ada Lovelace", Role.ADMIN);
    }

    @GetMapping
    @MuonicaOperation(summary = "Find users", description = "Searches the demo directory with optional filters and a request correlation id.")
    @MuonicaDocumentation(file = "classpath:/muonica/users/find-users.md")
    List<UserResponse> findUsers(@RequestParam(required = false) String query,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestParam(required = false) LocalDate createdAfter) {
        return List.of(new UserResponse(1, query == null ? "Ada Lovelace" : query, Role.MEMBER));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @MuonicaOperation(summary = "Create a user", description = "Creates a user and returns the persisted representation.")
    @MuonicaDocumentation(file = "classpath:/muonica/users/create-user.md")
    @MuonicaResponse(status = 409, description = "A user with this name already exists", body = ErrorResponse.class)
    @MuonicaSecurityRequirement("apiKey")
    UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return new UserResponse(2, request.name(), request.role());
    }

    @PatchMapping("/{id}")
    @MuonicaOperation(summary = "Update a user", description = "Applies a partial profile update without changing the user's identifier.")
    @MuonicaDocumentation(file = "classpath:/muonica/users/update-user.md")
    @MuonicaResponse(status = 404, description = "User was not found", body = ErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    UserResponse updateUser(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        return new UserResponse(id, request.name() == null ? "Ada Lovelace" : request.name(),
                request.role() == null ? Role.MEMBER : request.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @MuonicaOperation(summary = "Delete a user", description = "Permanently removes a user from the demo directory.")
    @MuonicaDocumentation(file = "classpath:/muonica/users/delete-user.md")
    @MuonicaResponse(status = 404, description = "User was not found", body = ErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    void deleteUser(@PathVariable long id) { }

    @GetMapping("/{id}/activity")
    @MuonicaOperation(summary = "List user activity", description = "Returns recent account events for audit and support workflows.")
    @MuonicaDocumentation(file = "classpath:/muonica/users/activity.md")
    @MuonicaResponse(status = 404, description = "User was not found", body = ErrorResponse.class)
    @MuonicaSecurityRequirement("bearerAuth")
    List<ActivityEvent> activity(@PathVariable long id,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return List.of(new ActivityEvent("profile.updated", "2026-01-15T10:30:00Z"));
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @MuonicaOperation(summary = "Upload an avatar", description = "Stores a square profile image for the selected user.")
    @MuonicaDocumentation(file = "classpath:/muonica/users/upload-avatar.md")
    @MuonicaResponse(status = 404, description = "User was not found", body = ErrorResponse.class)
    @MuonicaSecurityRequirement("apiKey")
    void uploadAvatar(@PathVariable long id, @RequestPart @NotNull MultipartFile avatar) { }

    record UserResponse(long id, String name, Role role) { }

    record CreateUserRequest(@NotBlank @Size(max = 80) String name, @NotNull Role role, List<@NotBlank String> tags) { }

    record UpdateUserRequest(@Size(max = 80) String name, Role role) { }

    record ActivityEvent(String type, String occurredAt) { }

    record ErrorResponse(String code, String message) { }

    enum Role { ADMIN, MEMBER }
}

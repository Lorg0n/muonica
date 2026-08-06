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
    @MuonicaOperation(summary = "Find users")
    List<UserResponse> findUsers(@RequestParam(required = false) String query,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestParam(required = false) LocalDate createdAfter) {
        return List.of(new UserResponse(1, query == null ? "Ada Lovelace" : query, Role.MEMBER));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @MuonicaOperation(summary = "Create a user")
    @MuonicaDocumentation(type = MuonicaDocumentation.Type.MERMAID, content = "sequenceDiagram\nclient->>api: POST /users\napi-->>client: 201")
    @MuonicaSecurityRequirement("apiKey")
    UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return new UserResponse(2, request.name(), request.role());
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @MuonicaOperation(summary = "Upload an avatar")
    void uploadAvatar(@PathVariable long id, @RequestPart @NotNull MultipartFile avatar) { }

    record UserResponse(long id, String name, Role role) { }

    record CreateUserRequest(@NotBlank @Size(max = 80) String name, @NotNull Role role, List<@NotBlank String> tags) { }

    record ErrorResponse(String code, String message) { }

    enum Role { ADMIN, MEMBER }
}

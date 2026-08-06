package io.muonica.demo.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
class UserController {
    @GetMapping("/{id}")
    UserResponse getUser(@PathVariable long id) {
        return new UserResponse(id, "Ada Lovelace");
    }

    @GetMapping
    List<UserResponse> findUsers(@RequestParam(required = false) String query) {
        return List.of(new UserResponse(1, query == null ? "Ada Lovelace" : query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return new UserResponse(2, request.name());
    }

    record UserResponse(long id, String name) { }

    record CreateUserRequest(@NotBlank @Size(max = 80) String name) { }
}

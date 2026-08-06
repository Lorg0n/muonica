package io.muonica.demo;

import io.muonica.core.annotation.MuonicaDocumentation;
import io.muonica.core.annotation.MuonicaProject;
import io.muonica.core.annotation.MuonicaSecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MuonicaProject(title = "Muonica demo API", version = "0.1.0", description = "A Spring MVC application used to demonstrate Muonica.")
@MuonicaDocumentation(file = "classpath:/muonica/index.md")
@MuonicaSecurityScheme(name = "bearerAuth", type = MuonicaSecurityScheme.Type.HTTP, scheme = "bearer", bearerFormat = "JWT")
@MuonicaSecurityScheme(name = "apiKey", type = MuonicaSecurityScheme.Type.API_KEY, parameterName = "X-API-Key")
public class MuonicaDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MuonicaDemoApplication.class, args);
    }
}

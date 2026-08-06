package io.muonica.spring.web;

import io.muonica.core.model.ApiEndpoint;
import io.muonica.spring.scan.MuonicaEndpointScanner;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class MuonicaDocumentationController {
    private final MuonicaEndpointScanner scanner;

    public MuonicaDocumentationController(MuonicaEndpointScanner scanner) {
        this.scanner = scanner;
    }

    @GetMapping("/muonica/api")
    public EndpointCatalog endpoints() {
        return new EndpointCatalog(scanner.scan());
    }

    public record EndpointCatalog(List<ApiEndpoint> endpoints) { }
}

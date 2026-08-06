package io.muonica.spring.web;

import io.muonica.core.model.ApiProject;
import io.muonica.openapi.OpenApiExporter;
import io.muonica.spring.scan.MuonicaEndpointScanner;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class MuonicaDocumentationController {
    private final MuonicaEndpointScanner scanner;
    private final OpenApiExporter openApiExporter = new OpenApiExporter();

    public MuonicaDocumentationController(MuonicaEndpointScanner scanner) { this.scanner = scanner; }

    @GetMapping(value = "/muonica", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return "<!doctype html><html><head><title>Muonica</title></head><body><h1>Muonica</h1>"
                + "<ul><li><a href=\"/muonica/api\">Muonica JSON</a></li><li><a href=\"/muonica/openapi.json\">OpenAPI 3.1 JSON</a></li></ul></body></html>";
    }

    @GetMapping("/muonica/api")
    public ApiProject api() { return scanner.scan(); }

    @GetMapping("/muonica/openapi.json")
    public Map<String, Object> openapi() { return openApiExporter.export(scanner.scan()); }
}

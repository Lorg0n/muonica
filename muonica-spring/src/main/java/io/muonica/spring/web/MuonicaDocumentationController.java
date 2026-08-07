package io.muonica.spring.web;

import io.muonica.core.model.api.ApiProject;
import io.muonica.openapi.OpenApiExporter;
import io.muonica.spring.scan.MuonicaEndpointScanner;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public final class MuonicaDocumentationController {
    private final MuonicaEndpointScanner scanner;
    private final OpenApiExporter openApiExporter = new OpenApiExporter();

    public MuonicaDocumentationController(MuonicaEndpointScanner scanner) { this.scanner = scanner; }

    @GetMapping(value = "/muonica", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return "redirect:/muonica/index.html";
    }

    @GetMapping("/muonica/api")
    @ResponseBody
    public ApiProject api() { return scanner.scan(); }

    @GetMapping("/muonica/openapi.json")
    @ResponseBody
    public Map<String, Object> openapi() { return openApiExporter.export(scanner.scan()); }
}

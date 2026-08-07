package io.muonica.spring.web;

import io.muonica.core.model.api.ApiProject;
import io.muonica.openapi.OpenApiExporter;
import io.muonica.spring.config.MuonicaWebProperties;
import io.muonica.spring.scan.MuonicaEndpointScanner;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public final class MuonicaDocumentationController {
    private final MuonicaEndpointScanner scanner;
    private final MuonicaWebProperties webProperties;
    private final OpenApiExporter openApiExporter = new OpenApiExporter();

    public MuonicaDocumentationController(MuonicaEndpointScanner scanner, MuonicaWebProperties webProperties) {
        this.scanner = scanner;
        this.webProperties = webProperties;
    }

    public String home() {
        return "redirect:" + webProperties.path() + "/";
    }

    @ResponseBody
    public Resource index() {
        return new ClassPathResource("META-INF/muonica/index.html");
    }

    @ResponseBody
    public ApiProject api() { return scanner.scan(); }

    @ResponseBody
    public Map<String, Object> openapi() { return openApiExporter.export(scanner.scan()); }
}

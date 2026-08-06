package io.muonica.spring.config;

import io.muonica.spring.scan.MuonicaEndpointScanner;
import io.muonica.spring.web.MuonicaDocumentationController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MuonicaAutoConfiguration {

    @Bean
    MuonicaEndpointScanner muonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping) {
        return new MuonicaEndpointScanner(handlerMapping);
    }

    @Bean
    MuonicaDocumentationController muonicaDocumentationController(MuonicaEndpointScanner scanner) {
        return new MuonicaDocumentationController(scanner);
    }
}

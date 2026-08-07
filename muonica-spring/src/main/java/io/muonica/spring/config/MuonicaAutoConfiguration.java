package io.muonica.spring.config;

import io.muonica.spring.scan.MuonicaEndpointScanner;
import io.muonica.spring.web.MuonicaDocumentationController;
import java.lang.reflect.Method;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.MediaType;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(MuonicaWebProperties.class)
public class MuonicaAutoConfiguration {

    @Bean
    MuonicaEndpointScanner muonicaEndpointScanner(RequestMappingHandlerMapping handlerMapping, ApplicationContext applicationContext, Environment environment) {
        return new MuonicaEndpointScanner(handlerMapping, applicationContext, environment);
    }

    @Bean
    MuonicaDocumentationController muonicaDocumentationController(MuonicaEndpointScanner scanner, MuonicaWebProperties webProperties) {
        return new MuonicaDocumentationController(scanner, webProperties);
    }

    @Bean
    WebMvcConfigurer muonicaWebMvcConfigurer(MuonicaWebProperties webProperties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String resourcePattern = webProperties.isRootPath() ? "/**" : webProperties.path() + "/**";
                registry.addResourceHandler(resourcePattern)
                        .addResourceLocations("classpath:/META-INF/muonica/");
            }
        };
    }

    @Bean
    SmartInitializingSingleton muonicaDocumentationMappings(RequestMappingHandlerMapping handlerMapping,
            MuonicaDocumentationController controller, MuonicaWebProperties webProperties) {
        return () -> {
            if (webProperties.isRootPath()) {
                register(handlerMapping, controller, "/", "index", MediaType.TEXT_HTML_VALUE);
            } else {
                register(handlerMapping, controller, webProperties.path(), "home", MediaType.TEXT_HTML_VALUE);
                register(handlerMapping, controller, webProperties.path() + "/", "index", MediaType.TEXT_HTML_VALUE);
            }
            register(handlerMapping, controller, webProperties.childPath("/api"), "api", null);
            register(handlerMapping, controller, webProperties.childPath("/openapi.json"), "openapi", null);
        };
    }

    private static void register(RequestMappingHandlerMapping handlerMapping, MuonicaDocumentationController controller,
            String path, String methodName, String produces) {
        Method method = ReflectionUtils.findMethod(MuonicaDocumentationController.class, methodName);
        RequestMappingInfo.Builder mapping = RequestMappingInfo.paths(path).methods(RequestMethod.GET);
        if (produces != null) {
            mapping.produces(produces);
        }
        handlerMapping.registerMapping(mapping.build(), controller, method);
    }
}

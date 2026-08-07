package io.muonica.spring.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.muonica.core.annotation.api.MuonicaProject;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.spring.documentation.DocumentationResolution;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class MuonicaEndpointScannerTest {

    @Test
    void resolvesControllerDocumentationOnceForAllControllerEndpoints() throws Exception {
        TestController controller = new TestController();
        Method firstMethod = TestController.class.getDeclaredMethod("first");
        Method secondMethod = TestController.class.getDeclaredMethod("second");
        RequestMappingInfo firstMapping = RequestMappingInfo.paths("/first").methods(RequestMethod.GET).build();
        RequestMappingInfo secondMapping = RequestMappingInfo.paths("/second").methods(RequestMethod.GET).build();

        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                firstMapping, new HandlerMethod(controller, firstMethod),
                secondMapping, new HandlerMethod(controller, secondMethod)));

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeanNamesForAnnotation(MuonicaProject.class)).thenReturn(new String[0]);
        when(applicationContext.getBeanNamesForAnnotation(MuonicaDocumentation.class)).thenReturn(new String[0]);

        AtomicInteger controllerResolutions = new AtomicInteger();
        Function<AnnotatedElement, DocumentationResolution> resolver = source -> {
            if (source instanceof Class<?>) controllerResolutions.incrementAndGet();
            return DocumentationResolution.empty();
        };

        MuonicaEndpointScanner scanner = new MuonicaEndpointScanner(handlerMapping, applicationContext,
                new MockEnvironment(), resolver);

        assertEquals(2, scanner.scan().groups().get(0).endpoints().size());
        assertEquals(1, controllerResolutions.get());
    }

    @Test
    void excludesErrorControllerMappings() throws Exception {
        TestController controller = new TestController();
        ErrorTestController errorController = new ErrorTestController();
        Method endpoint = TestController.class.getDeclaredMethod("first");
        Method jsonError = ErrorTestController.class.getDeclaredMethod("jsonError");
        Method htmlError = ErrorTestController.class.getDeclaredMethod("htmlError");
        RequestMappingInfo endpointMapping = RequestMappingInfo.paths("/first").methods(RequestMethod.GET).build();
        RequestMappingInfo jsonErrorMapping = RequestMappingInfo.paths("/error").build();
        RequestMappingInfo htmlErrorMapping = RequestMappingInfo.paths("/error").produces("text/html").build();

        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                endpointMapping, new HandlerMethod(controller, endpoint),
                jsonErrorMapping, new HandlerMethod(errorController, jsonError),
                htmlErrorMapping, new HandlerMethod(errorController, htmlError)));

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeanNamesForAnnotation(MuonicaProject.class)).thenReturn(new String[0]);
        when(applicationContext.getBeanNamesForAnnotation(MuonicaDocumentation.class)).thenReturn(new String[0]);

        MuonicaEndpointScanner scanner = new MuonicaEndpointScanner(handlerMapping, applicationContext,
                new MockEnvironment(), ignored -> DocumentationResolution.empty());

        assertEquals(1, scanner.scan().groups().size());
        assertEquals("TestController", scanner.scan().groups().get(0).name());
        assertEquals("/first", scanner.scan().groups().get(0).endpoints().get(0).path());
    }

    static final class TestController {
        @RequestMapping(path = "/first", method = RequestMethod.GET)
        public String first() {
            return "first";
        }

        @RequestMapping(path = "/second", method = RequestMethod.GET)
        public String second() {
            return "second";
        }
    }

    static final class ErrorTestController implements ErrorController {
        @RequestMapping("/error")
        public String jsonError() {
            return "error";
        }

        @RequestMapping(path = "/error", produces = "text/html")
        public String htmlError() {
            return "error";
        }
    }
}

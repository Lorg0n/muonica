package io.muonica.spring.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.muonica.core.annotation.api.MuonicaResponse;
import io.muonica.core.annotation.api.MuonicaProject;
import io.muonica.core.annotation.documentation.MuonicaDocumentation;
import io.muonica.core.annotation.documentation.MuonicaPage;
import io.muonica.core.model.documentation.DocumentationBlock;
import io.muonica.core.model.documentation.DocumentationWarning;
import io.muonica.spring.documentation.DocumentationResolution;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @Test
    void addsResponsesDeclaredByGlobalExceptionAdviceWithoutOverridingEndpointResponses() throws Exception {
        TestController controller = new TestController();
        Method endpoint = TestController.class.getDeclaredMethod("first");
        RequestMappingInfo endpointMapping = RequestMappingInfo.paths("/first").methods(RequestMethod.GET).build();

        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                endpointMapping, new HandlerMethod(controller, endpoint)));

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeanNamesForAnnotation(MuonicaProject.class)).thenReturn(new String[0]);
        when(applicationContext.getBeanNamesForAnnotation(MuonicaDocumentation.class)).thenReturn(new String[0]);
        when(applicationContext.getBeansWithAnnotation(RestControllerAdvice.class)).thenReturn(Map.of(
                "testExceptionHandler", new TestExceptionHandler()));
        when(applicationContext.getBeansWithAnnotation(org.springframework.web.bind.annotation.ControllerAdvice.class))
                .thenReturn(Map.of());

        MuonicaEndpointScanner scanner = new MuonicaEndpointScanner(handlerMapping, applicationContext,
                new MockEnvironment(), ignored -> DocumentationResolution.empty());

        var responses = scanner.scan().groups().get(0).endpoints().get(0).responses();
        assertEquals(3, responses.size());
        assertEquals("Endpoint-specific bad request", responses.stream()
                .filter(response -> response.statusCode().equals("400"))
                .findFirst().orElseThrow().description());
        assertEquals("Internal server error", responses.stream()
                .filter(response -> response.statusCode().equals("500"))
                .findFirst().orElseThrow().description());
        assertEquals("TestError", responses.stream()
                .filter(response -> response.statusCode().equals("500"))
                .findFirst().orElseThrow().content().get("application/json").ref());
    }

    @Test
    void skipsUnresolvablePagesAndRetainsTheirWarningsInLenientMode() throws Exception {
        MuonicaEndpointScanner scanner = scannerForPages(SourceFailureProject.class,
                (page, source) -> new DocumentationResolution(List.of(), List.of(
                        new DocumentationWarning("INVALID_PAGE_SOURCE", page.title(), null, "Page source is invalid"))));

        var project = scanner.scan();

        assertEquals(List.of(), project.documentationPages());
        assertEquals("INVALID_PAGE_SOURCE", project.documentationWarnings().get(0).type());
    }

    @Test
    void skipsDuplicatePagesAndRetainsTheirWarningsInLenientMode() throws Exception {
        DocumentationBlock block = new DocumentationBlock("markdown", "Page", Map.of("source", "test"));
        MuonicaEndpointScanner scanner = scannerForPages(DuplicatePageProject.class,
                (page, source) -> new DocumentationResolution(List.of(block), List.of()));

        var project = scanner.scan();

        assertEquals(1, project.documentationPages().size());
        assertEquals("DUPLICATE_PAGE_TITLE", project.documentationWarnings().get(0).type());
    }

    private MuonicaEndpointScanner scannerForPages(Class<?> projectType,
            BiFunction<MuonicaPage, AnnotatedElement, DocumentationResolution> pageResolver) throws Exception {
        TestController controller = new TestController();
        Method endpoint = TestController.class.getDeclaredMethod("first");
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                RequestMappingInfo.paths("/first").methods(RequestMethod.GET).build(), new HandlerMethod(controller, endpoint)));

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeanNamesForAnnotation(MuonicaProject.class)).thenReturn(new String[] {"project"});
        org.mockito.Mockito.doReturn(projectType).when(applicationContext).getType("project");
        when(applicationContext.findAnnotationOnBean("project", MuonicaProject.class))
                .thenReturn(projectType.getAnnotation(MuonicaProject.class));
        return new MuonicaEndpointScanner(handlerMapping, applicationContext, new MockEnvironment(),
                ignored -> DocumentationResolution.empty(), pageResolver, false);
    }

    static final class TestController {
        @RequestMapping(path = "/first", method = RequestMethod.GET)
        @MuonicaResponse(status = 400, description = "Endpoint-specific bad request", body = TestError.class)
        public String first() {
            return "first";
        }

        @RequestMapping(path = "/second", method = RequestMethod.GET)
        public String second() {
            return "second";
        }
    }

    @RestControllerAdvice
    static final class TestExceptionHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        @MuonicaResponse(status = 400, description = "Global bad request", body = TestError.class)
        public ResponseEntity<TestError> badRequest() {
            return ResponseEntity.badRequest().body(new TestError("bad_request"));
        }

        @ExceptionHandler(Exception.class)
        @MuonicaResponse(status = 500, description = "Internal server error", body = TestError.class)
        public ResponseEntity<TestError> internalError() {
            return ResponseEntity.internalServerError().body(new TestError("internal_error"));
        }
    }

    record TestError(String code) { }

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

    @MuonicaProject
    @MuonicaPage(title = "Broken", content = "ignored")
    static final class SourceFailureProject { }

    @MuonicaProject
    @MuonicaPage(title = "Overview", content = "first")
    @MuonicaPage(title = "Overview", content = "second")
    static final class DuplicatePageProject { }
}

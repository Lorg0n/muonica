package io.muonica.spring.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.muonica.core.model.api.ApiRequest;
import io.muonica.core.model.api.ApiSchema;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

class HandlerParameterResolverTest {

    @Test
    void accumulatesMultipartPropertiesAndRequiredParts() throws Exception {
        Method method = MultipartController.class.getDeclaredMethod(
                "upload", MultipartFile.class, String.class, MultipartFile.class);
        RequestMappingInfo mapping = RequestMappingInfo.paths("/upload")
                .consumes("multipart/form-data")
                .build();

        HandlerParameterResolver.Resolution resolution = new HandlerParameterResolver(new SchemaResolver())
                .resolve(method, mapping);

        ApiRequest request = resolution.request();
        ApiSchema schema = request.content().get("multipart/form-data");
        assertTrue(request.required());
        assertEquals(List.of("avatar", "document"), schema.requiredProperties());
        assertEquals(Set.of("avatar", "caption", "document"), schema.properties().keySet());
        assertEquals("binary", schema.properties().get("avatar").format());
        assertEquals("string", schema.properties().get("caption").type());
    }

    static final class MultipartController {
        void upload(
                @RequestPart("avatar") MultipartFile avatar,
                @RequestPart(name = "caption", required = false) String caption,
                @RequestPart("document") MultipartFile document) {
        }
    }
}

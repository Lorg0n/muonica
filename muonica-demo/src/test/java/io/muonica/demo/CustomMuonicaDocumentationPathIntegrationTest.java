package io.muonica.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "muonica.web.path=/reference/")
class CustomMuonicaDocumentationPathIntegrationTest {
    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void servesEveryDocumentationResourceBelowTheConfiguredPath() throws Exception {
        mockMvc.perform(get("/reference"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reference/"));
        mockMvc.perform(get("/reference/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("./js/app.js")));
        mockMvc.perform(get("/reference/js/app.js")).andExpect(status().isOk());
        mockMvc.perform(get("/reference/api")).andExpect(status().isOk());
        mockMvc.perform(get("/reference/openapi.json")).andExpect(status().isOk());
        mockMvc.perform(get("/docs")).andExpect(status().isNotFound());
    }
}

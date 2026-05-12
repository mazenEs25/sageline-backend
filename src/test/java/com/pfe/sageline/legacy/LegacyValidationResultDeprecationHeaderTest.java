package com.pfe.sageline.legacy;

import com.pfe.sageline.Config.LegacyResultsDeprecationFilter;
import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LegacyValidationResultDeprecationHeaderTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired LegacyResultsDeprecationFilter legacyFilter;
    @MockitoBean SecurityUtils securityUtils;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .addFilters(legacyFilter)
                .build();
    }

    @Test
    void legacyListEndpoint_hasDeprecationHeader() throws Exception {
        mockMvc.perform(get("/api/validation-results")
                        .with(jwt().authorities(() -> "ROLE_ADMIN_IT")))
                .andExpect(header().string("Deprecation", "true"));
    }

    @Test
    void legacyByValidationEndpoint_hasDeprecationHeader() throws Exception {
        mockMvc.perform(get("/api/validation-results/validation/1")
                        .with(jwt().authorities(() -> "ROLE_ADMIN_IT")))
                .andExpect(header().string("Deprecation", "true"));
    }

    @Test
    void newMeasuresEndpoint_hasNoDeprecationHeader() throws Exception {
        mockMvc.perform(get("/api/validations/99999/measures")
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(header().doesNotExist("Deprecation"));
    }
}


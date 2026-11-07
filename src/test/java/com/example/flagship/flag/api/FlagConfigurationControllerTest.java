package com.example.flagship.flag.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.flagship.flag.application.ConfigurationVersionConflictException;
import com.example.flagship.flag.application.FlagConfigurationQueryService;
import com.example.flagship.flag.application.UpdateFlagConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Error-contract test for the one failure the integration tests cannot trigger over HTTP
 * deterministically: a lost optimistic-lock race. The race itself is proven against PostgreSQL in
 * FlagConfigurationApiIT; this proves how the API reports it.
 */
@WebMvcTest(FlagConfigurationController.class)
class FlagConfigurationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FlagConfigurationQueryService queries;
    @MockitoBean UpdateFlagConfigurationService updates;

    @Test
    void versionConflictIsReportedAs409ProblemWithoutInternalDetails() throws Exception {
        when(updates.update(eq("new-checkout"), eq("prod"), any())).thenThrow(
                new ConfigurationVersionConflictException("new-checkout", "prod",
                        new OptimisticLockingFailureException("Row was updated or deleted by another transaction")));

        mvc.perform(put("/api/v1/flags/{key}/configurations/{env}", "new-checkout", "prod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"rolloutPercentage":20,"allowUsers":[],"denyUsers":[]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/api/v1/flags/new-checkout/configurations/prod"))
                .andExpect(jsonPath("$.detail").value(
                        "Configuration for flag 'new-checkout' in environment 'prod' was changed concurrently; "
                                + "re-read it and retry"));
    }
}

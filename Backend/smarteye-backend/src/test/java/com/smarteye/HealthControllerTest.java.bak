package com.smarteye.controller;

import com.smarteye.config.SmartEyeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SmartEyeProperties smartEyeProperties;
    
    @BeforeEach
    void setUp() {
        // Mock properties setup
        SmartEyeProperties.Upload upload = new SmartEyeProperties.Upload();
        upload.setDirectory("./test-uploads");
        upload.setTempDirectory("./test-temp");
        
        SmartEyeProperties.Processing processing = new SmartEyeProperties.Processing();
        processing.setMaxConcurrentJobs(2);
        
        SmartEyeProperties.Api.LamService lamService = new SmartEyeProperties.Api.LamService();
        lamService.setBaseUrl("http://localhost:8001");
        
        SmartEyeProperties.Api api = new SmartEyeProperties.Api();
        api.setLamService(lamService);
        
        when(smartEyeProperties.getUpload()).thenReturn(upload);
        when(smartEyeProperties.getProcessing()).thenReturn(processing);
        when(smartEyeProperties.getApi()).thenReturn(api);
    }

    @Test
    void healthEndpoint_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/health")
                .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.application", is("SmartEye Backend")))
                .andExpect(jsonPath("$.system", notNullValue()));
    }

    @Test
    void infoEndpoint_ShouldReturnApplicationInfo() throws Exception {
        mockMvc.perform(get("/api/info")
                .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.application.name", is("SmartEye Backend")))
                .andExpect(jsonPath("$.features.ocrSupport", is(true)))
                .andExpect(jsonPath("$.supportedFormats.images", notNullValue()));
    }
    
    @Test
    void readyEndpoint_ShouldReturnReadiness() throws Exception {
        mockMvc.perform(get("/api/ready")
                .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.uploadDirectory", is("OK")))
                .andExpect(jsonPath("$.tempDirectory", is("OK")));
    }
}
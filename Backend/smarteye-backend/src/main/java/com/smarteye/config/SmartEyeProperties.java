package com.smarteye.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "smarteye")
public class SmartEyeProperties {
    
    private Upload upload = new Upload();
    private Processing processing = new Processing();
    private Models models = new Models();
    private Api api = new Api();
    
    // Getters and Setters
    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }
    
    public Processing getProcessing() { return processing; }
    public void setProcessing(Processing processing) { this.processing = processing; }
    
    public Models getModels() { return models; }
    public void setModels(Models models) { this.models = models; }
    
    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }
    
    public static class Upload {
        private String directory = "./uploads";
        private String tempDirectory = "./temp";
        private String maxFileSize = "50MB";
        
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        
        public String getTempDirectory() { return tempDirectory; }
        public void setTempDirectory(String tempDirectory) { this.tempDirectory = tempDirectory; }
        
        public String getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(String maxFileSize) { this.maxFileSize = maxFileSize; }
    }
    
    public static class Processing {
        private int batchSize = 10;
        private int maxConcurrentJobs = 5;
        
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        
        public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
        public void setMaxConcurrentJobs(int maxConcurrentJobs) { this.maxConcurrentJobs = maxConcurrentJobs; }
    }
    
    public static class Models {
        private Tesseract tesseract = new Tesseract();
        
        public Tesseract getTesseract() { return tesseract; }
        public void setTesseract(Tesseract tesseract) { this.tesseract = tesseract; }
        
        public static class Tesseract {
            private String path = "/usr/bin/tesseract";
            private String lang = "kor+eng";
            private String config = "--oem 3 --psm 6";
            
            public String getPath() { return path; }
            public void setPath(String path) { this.path = path; }
            
            public String getLang() { return lang; }
            public void setLang(String lang) { this.lang = lang; }
            
            public String getConfig() { return config; }
            public void setConfig(String config) { this.config = config; }
        }
    }
    
    public static class Api {
        private OpenAI openai = new OpenAI();
        private LamService lamService = new LamService();
        
        public OpenAI getOpenai() { return openai; }
        public void setOpenai(OpenAI openai) { this.openai = openai; }
        
        public LamService getLamService() { return lamService; }
        public void setLamService(LamService lamService) { this.lamService = lamService; }
        
        public static class OpenAI {
            private String baseUrl = "https://api.openai.com/v1";
            private String key;
            private String model = "gpt-4-turbo";
            private int maxTokens = 600;
            private double temperature = 0.2;
            
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            
            public String getKey() { return key; }
            public void setKey(String key) { this.key = key; }
            
            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
            
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            
            public double getTemperature() { return temperature; }
            public void setTemperature(double temperature) { this.temperature = temperature; }
        }
        
        public static class LamService {
            private String baseUrl = "http://localhost:8001";
            private Duration timeout = Duration.ofSeconds(30);
            private int retryAttempts = 3;
            
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            
            public Duration getTimeout() { return timeout; }
            public void setTimeout(Duration timeout) { this.timeout = timeout; }
            
            public int getRetryAttempts() { return retryAttempts; }
            public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
        }
    }
}
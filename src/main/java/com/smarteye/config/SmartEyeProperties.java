package com.smarteye.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "smarteye")
@Data
public class SmartEyeProperties {
    
    private Upload upload = new Upload();
    private Ocr ocr = new Ocr();
    private Api api = new Api();
    private Model model = new Model();
    private Processing processing = new Processing();
    private Cache cache = new Cache();
    
    @Data
    public static class Upload {
        private String tempDir = "./temp";
        private String allowedExtensions = "jpg,jpeg,png,pdf,tiff,bmp";
        private String maxFileSize = "50MB";
    }
    
    @Data
    public static class Ocr {
        private Tesseract tesseract = new Tesseract();
        
        @Data
        public static class Tesseract {
            private String path = "/usr/bin/tesseract";
            private String tessdataPath = "./src/main/resources/tessdata";
            private String language = "kor+eng";
            private int pageSegMode = 3;
            private int oem = 3;
        }
    }
    
    @Data
    public static class Api {
        private OpenAI openai = new OpenAI();
        
        @Data
        public static class OpenAI {
            private String baseUrl = "https://api.openai.com/v1";
            private String apiKey;
            private String model = "gpt-4-vision-preview";
            private String timeout = "30s";
            private int maxTokens = 4096;
        }
    }
    
    @Data
    public static class Model {
        private Yolo yolo = new Yolo();
        
        @Data
        public static class Yolo {
            private String modelPath = "./src/main/resources/models";
            private double confidenceThreshold = 0.25;
            private double iouThreshold = 0.45;
            private int inputSize = 1024;
        }
    }
    
    @Data
    public static class Processing {
        private int maxParallelTasks = 4;
        private String timeout = "300s";
    }
    
    @Data
    public static class Cache {
        private boolean enabled = true;
        private String ttl = "3600s";
    }
}

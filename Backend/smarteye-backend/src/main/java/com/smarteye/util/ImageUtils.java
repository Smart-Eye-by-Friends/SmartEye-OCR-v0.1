package com.smarteye.util;

import com.smarteye.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Component
public class ImageUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageUtils.class);
    
    public BufferedImage loadImage(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            BufferedImage image = ImageIO.read(imageFile);
            
            if (image == null) {
                throw new FileProcessingException("이미지 파일을 읽을 수 없습니다: " + imagePath);
            }
            
            logger.debug("Image loaded successfully: {} ({}x{})", imagePath, image.getWidth(), image.getHeight());
            return image;
            
        } catch (IOException e) {
            logger.error("Failed to load image: {} - {}", imagePath, e.getMessage(), e);
            throw new FileProcessingException("이미지 로드에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage loadImage(InputStream inputStream) {
        try {
            BufferedImage image = ImageIO.read(inputStream);
            
            if (image == null) {
                throw new FileProcessingException("이미지 스트림을 읽을 수 없습니다");
            }
            
            logger.debug("Image loaded successfully from stream ({}x{})", image.getWidth(), image.getHeight());
            return image;
            
        } catch (IOException e) {
            logger.error("Failed to load image from stream: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지 스트림 로드에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public void saveImage(BufferedImage image, String outputPath, String format) {
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            
            boolean success = ImageIO.write(image, format, outputFile);
            if (!success) {
                throw new FileProcessingException("지원되지 않는 이미지 형식: " + format);
            }
            
            logger.debug("Image saved successfully: {}", outputPath);
            
        } catch (IOException e) {
            logger.error("Failed to save image: {} - {}", outputPath, e.getMessage(), e);
            throw new FileProcessingException("이미지 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public byte[] imageToByteArray(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, format, baos);
            return baos.toByteArray();
            
        } catch (IOException e) {
            logger.error("Failed to convert image to byte array: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지를 바이트 배열로 변환하는데 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        logger.debug("Resizing image from {}x{} to {}x{}", 
            originalImage.getWidth(), originalImage.getHeight(), targetWidth, targetHeight);
        
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, originalImage.getType());
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Enable high quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        
        return resizedImage;
    }
    
    public BufferedImage resizeImageKeepAspectRatio(BufferedImage originalImage, int maxWidth, int maxHeight) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        // Calculate new dimensions maintaining aspect ratio
        double aspectRatio = (double) originalWidth / originalHeight;
        int newWidth, newHeight;
        
        if (originalWidth > originalHeight) {
            newWidth = Math.min(maxWidth, originalWidth);
            newHeight = (int) (newWidth / aspectRatio);
        } else {
            newHeight = Math.min(maxHeight, originalHeight);
            newWidth = (int) (newHeight * aspectRatio);
        }
        
        return resizeImage(originalImage, newWidth, newHeight);
    }
    
    public BufferedImage rotateImage(BufferedImage originalImage, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        int newWidth = (int) (originalWidth * cos + originalHeight * sin);
        int newHeight = (int) (originalWidth * sin + originalHeight * cos);
        
        BufferedImage rotatedImage = new BufferedImage(newWidth, newHeight, originalImage.getType());
        Graphics2D g2d = rotatedImage.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.translate(newWidth / 2, newHeight / 2);
        g2d.rotate(radians);
        g2d.drawImage(originalImage, -originalWidth / 2, -originalHeight / 2, null);
        g2d.dispose();
        
        logger.debug("Image rotated by {} degrees", degrees);
        return rotatedImage;
    }
    
    public BufferedImage cropImage(BufferedImage originalImage, int x, int y, int width, int height) {
        // Validate crop bounds
        int imgWidth = originalImage.getWidth();
        int imgHeight = originalImage.getHeight();
        
        x = Math.max(0, Math.min(x, imgWidth - 1));
        y = Math.max(0, Math.min(y, imgHeight - 1));
        width = Math.min(width, imgWidth - x);
        height = Math.min(height, imgHeight - y);
        
        BufferedImage croppedImage = originalImage.getSubimage(x, y, width, height);
        logger.debug("Image cropped: ({}, {}, {}, {})", x, y, width, height);
        
        return croppedImage;
    }
    
    public BufferedImage convertToRGB(BufferedImage originalImage) {
        if (originalImage.getType() == BufferedImage.TYPE_INT_RGB) {
            return originalImage;
        }
        
        BufferedImage rgbImage = new BufferedImage(
            originalImage.getWidth(), 
            originalImage.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, null);
        g2d.dispose();
        
        logger.debug("Image converted to RGB format");
        return rgbImage;
    }
    
    public boolean isValidImageDimensions(BufferedImage image, int minWidth, int minHeight, int maxWidth, int maxHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        return width >= minWidth && width <= maxWidth && height >= minHeight && height <= maxHeight;
    }
}
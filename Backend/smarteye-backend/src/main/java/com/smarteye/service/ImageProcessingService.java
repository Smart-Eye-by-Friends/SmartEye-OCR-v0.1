package com.smarteye.service;

import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.exception.FileProcessingException;
import com.smarteye.util.ImageUtils;
import com.smarteye.util.CoordinateScalingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ImageProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingService.class);
    
    @Autowired
    private ImageUtils imageUtils;
    
    @Value("${smarteye.static.directory:./static}")
    private String staticDirectory;
    
    public BufferedImage loadImageFromFile(String imagePath) {
        try {
            logger.debug("이미지 로드 시작: {}", imagePath);
            
            BufferedImage image = imageUtils.loadImage(imagePath);
            
            logger.debug("이미지 로드 완료: {} ({}x{})", imagePath, image.getWidth(), image.getHeight());
            return image;
            
        } catch (Exception e) {
            logger.error("이미지 로드 실패: {} - {}", imagePath, e.getMessage(), e);
            throw new FileProcessingException("이미지를 로드할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage loadImageFromMultipartFile(MultipartFile file) {
        try {
            logger.debug("MultipartFile에서 이미지 로드: {}", file.getOriginalFilename());
            
            try (InputStream inputStream = file.getInputStream()) {
                BufferedImage image = imageUtils.loadImage(inputStream);
                
                logger.debug("이미지 로드 완료: {} ({}x{})", 
                           file.getOriginalFilename(), image.getWidth(), image.getHeight());
                return image;
            }
            
        } catch (IOException e) {
            logger.error("MultipartFile 이미지 로드 실패: {} - {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new FileProcessingException("업로드된 이미지를 로드할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public CompletableFuture<BufferedImage> loadImageAsync(String imagePath) {
        return CompletableFuture.supplyAsync(() -> loadImageFromFile(imagePath));
    }
    
    public void saveImage(BufferedImage image, String outputPath, String format) {
        try {
            logger.debug("이미지 저장 시작: {} (형식: {})", outputPath, format);
            
            imageUtils.saveImage(image, outputPath, format);
            
            logger.debug("이미지 저장 완료: {}", outputPath);
            
        } catch (Exception e) {
            logger.error("이미지 저장 실패: {} - {}", outputPath, e.getMessage(), e);
            throw new FileProcessingException("이미지를 저장할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public CompletableFuture<Void> saveImageAsync(BufferedImage image, String outputPath, String format) {
        return CompletableFuture.runAsync(() -> saveImage(image, outputPath, format));
    }
    
    /**
     * 레이아웃 시각화 이미지를 생성하고 파일로 저장
     * @param originalImage 원본 이미지
     * @param layoutInfo 레이아웃 정보 리스트
     * @param jobId 분석 작업 ID (파일명에 사용)
     * @return 저장된 시각화 이미지의 정적 파일 경로
     */
    public String generateAndSaveLayoutVisualization(BufferedImage originalImage, List<LayoutInfo> layoutInfo, String jobId) {
        try {
            logger.info("레이아웃 시각화 이미지 생성 시작 - JobID: {}, 요소 수: {}", jobId, layoutInfo.size());
            
            // 1. 바운딩 박스가 그려진 이미지 생성
            BufferedImage visualizationImage = drawLayoutBoxes(originalImage, layoutInfo, true);
            
            // 2. 시각화 이미지 저장 경로 생성
            String fileName = String.format("layout_viz_%s_%d.png", jobId, System.currentTimeMillis());
            String staticPath = "/static/" + fileName;
            String fullPath = staticDirectory + "/" + fileName;
            
            // 3. 정적 디렉토리 확인 및 생성
            ensureStaticDirectoryExists();
            
            // 4. 이미지 저장
            saveImage(visualizationImage, fullPath, "PNG");
            
            logger.info("레이아웃 시각화 이미지 저장 완료 - 경로: {}", staticPath);
            return staticPath;
            
        } catch (Exception e) {
            logger.error("레이아웃 시각화 이미지 생성 실패 - JobID: {}", jobId, e);
            throw new RuntimeException("레이아웃 시각화 이미지 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    /**
     * 정적 디렉토리가 존재하는지 확인하고 없으면 생성
     */
    private void ensureStaticDirectoryExists() throws IOException {
        java.nio.file.Path staticPath = java.nio.file.Paths.get(staticDirectory);
        if (!java.nio.file.Files.exists(staticPath)) {
            java.nio.file.Files.createDirectories(staticPath);
            logger.info("정적 디렉토리 생성: {}", staticDirectory);
        }
    }
    
    public byte[] imageToByteArray(BufferedImage image, String format) {
        try {
            logger.debug("이미지를 바이트 배열로 변환 (형식: {})", format);
            
            byte[] imageBytes = imageUtils.imageToByteArray(image, format);
            
            logger.debug("이미지 바이트 배열 변환 완료 ({} bytes)", imageBytes.length);
            return imageBytes;
            
        } catch (Exception e) {
            logger.error("이미지 바이트 배열 변환 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지를 바이트 배열로 변환할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        try {
            logger.debug("이미지 크기 조정: {}x{} -> {}x{}", 
                       originalImage.getWidth(), originalImage.getHeight(), targetWidth, targetHeight);
            
            BufferedImage resizedImage = imageUtils.resizeImage(originalImage, targetWidth, targetHeight);
            
            logger.debug("이미지 크기 조정 완료");
            return resizedImage;
            
        } catch (Exception e) {
            logger.error("이미지 크기 조정 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지 크기를 조정할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage resizeImageKeepAspectRatio(BufferedImage originalImage, int maxWidth, int maxHeight) {
        try {
            logger.debug("이미지 비율 유지 크기 조정: {}x{} -> 최대 {}x{}", 
                       originalImage.getWidth(), originalImage.getHeight(), maxWidth, maxHeight);
            
            BufferedImage resizedImage = imageUtils.resizeImageKeepAspectRatio(originalImage, maxWidth, maxHeight);
            
            logger.debug("이미지 비율 유지 크기 조정 완료: {}x{}", 
                       resizedImage.getWidth(), resizedImage.getHeight());
            return resizedImage;
            
        } catch (Exception e) {
            logger.error("이미지 비율 유지 크기 조정 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지 비율 유지 크기 조정에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage rotateImage(BufferedImage originalImage, double degrees) {
        try {
            logger.debug("이미지 회전: {}도", degrees);
            
            BufferedImage rotatedImage = imageUtils.rotateImage(originalImage, degrees);
            
            logger.debug("이미지 회전 완료: {}x{}", rotatedImage.getWidth(), rotatedImage.getHeight());
            return rotatedImage;
            
        } catch (Exception e) {
            logger.error("이미지 회전 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지를 회전할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage cropImage(BufferedImage originalImage, int x, int y, int width, int height) {
        try {
            logger.debug("이미지 자르기: ({}, {}, {}, {})", x, y, width, height);
            
            BufferedImage croppedImage = imageUtils.cropImage(originalImage, x, y, width, height);
            
            logger.debug("이미지 자르기 완료: {}x{}", croppedImage.getWidth(), croppedImage.getHeight());
            return croppedImage;
            
        } catch (Exception e) {
            logger.error("이미지 자르기 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지를 자를 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public BufferedImage convertToRGB(BufferedImage originalImage) {
        try {
            logger.debug("이미지 RGB 변환: {}", originalImage.getType());
            
            BufferedImage rgbImage = imageUtils.convertToRGB(originalImage);
            
            logger.debug("이미지 RGB 변환 완료");
            return rgbImage;
            
        } catch (Exception e) {
            logger.error("이미지 RGB 변환 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지를 RGB로 변환할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    public boolean validateImageDimensions(BufferedImage image, int minWidth, int minHeight, int maxWidth, int maxHeight) {
        boolean isValid = imageUtils.isValidImageDimensions(image, minWidth, minHeight, maxWidth, maxHeight);
        
        logger.debug("이미지 크기 유효성 검사: {}x{} (최소: {}x{}, 최대: {}x{}) -> {}", 
                   image.getWidth(), image.getHeight(), minWidth, minHeight, maxWidth, maxHeight, isValid);
        
        return isValid;
    }
    
    public BufferedImage preprocessImageForOCR(BufferedImage originalImage) {
        try {
            logger.debug("OCR 전처리 시작: {}x{}", originalImage.getWidth(), originalImage.getHeight());
            
            // 1. RGB 변환
            BufferedImage processedImage = convertToRGB(originalImage);
            
            // 2. 크기가 너무 작으면 확대
            if (processedImage.getWidth() < 300 || processedImage.getHeight() < 300) {
                int newWidth = Math.max(300, processedImage.getWidth());
                int newHeight = Math.max(300, processedImage.getHeight());
                processedImage = resizeImage(processedImage, newWidth, newHeight);
                logger.debug("이미지 확대: {}x{}", newWidth, newHeight);
            }
            
            // 3. 크기가 너무 크면 축소 (메모리 효율성)
            if (processedImage.getWidth() > 2048 || processedImage.getHeight() > 2048) {
                processedImage = resizeImageKeepAspectRatio(processedImage, 2048, 2048);
                logger.debug("이미지 축소: {}x{}", processedImage.getWidth(), processedImage.getHeight());
            }
            
            logger.debug("OCR 전처리 완료: {}x{}", processedImage.getWidth(), processedImage.getHeight());
            return processedImage;
            
        } catch (Exception e) {
            logger.error("OCR 전처리 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("OCR 전처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    public CompletableFuture<BufferedImage> preprocessImageForOCRAsync(BufferedImage originalImage) {
        return CompletableFuture.supplyAsync(() -> preprocessImageForOCR(originalImage));
    }
    
    public BufferedImage preprocessImageForAI(BufferedImage originalImage) {
        try {
            logger.debug("AI 분석 전처리 시작: {}x{}", originalImage.getWidth(), originalImage.getHeight());
            
            // 1. RGB 변환
            BufferedImage processedImage = convertToRGB(originalImage);
            
            // 2. AI 모델에 최적화된 크기로 조정 (보통 1024x1024 이하)
            if (processedImage.getWidth() > 1024 || processedImage.getHeight() > 1024) {
                processedImage = resizeImageKeepAspectRatio(processedImage, 1024, 1024);
                logger.debug("AI용 이미지 크기 조정: {}x{}", processedImage.getWidth(), processedImage.getHeight());
            }
            
            logger.debug("AI 분석 전처리 완료: {}x{}", processedImage.getWidth(), processedImage.getHeight());
            return processedImage;
            
        } catch (Exception e) {
            logger.error("AI 분석 전처리 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("AI 분석 전처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    public CompletableFuture<BufferedImage> preprocessImageForAIAsync(BufferedImage originalImage) {
        return CompletableFuture.supplyAsync(() -> preprocessImageForAI(originalImage));
    }
    
    public ImageMetadata extractImageMetadata(BufferedImage image) {
        try {
            ImageMetadata metadata = new ImageMetadata();
            metadata.setWidth(image.getWidth());
            metadata.setHeight(image.getHeight());
            metadata.setType(image.getType());
            metadata.setColorModel(image.getColorModel().toString());
            metadata.setHasAlpha(image.getColorModel().hasAlpha());
            
            logger.debug("이미지 메타데이터 추출 완료: {}x{}, 타입: {}", 
                       metadata.getWidth(), metadata.getHeight(), metadata.getType());
            
            return metadata;
            
        } catch (Exception e) {
            logger.error("이미지 메타데이터 추출 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("이미지 메타데이터를 추출할 수 없습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 레이아웃 바운딩 박스를 이미지에 그리기 (좌표 검증 및 로깅 강화)
     * Python api_server.py의 visualize_results() 메서드와 동일한 기능
     * @param image 원본 이미지
     * @param layoutInfo 레이아웃 정보 리스트
     * @return 바운딩 박스가 그려진 이미지
     */
    public BufferedImage drawLayoutBoxes(BufferedImage image, List<LayoutInfo> layoutInfo) {
        return drawLayoutBoxes(image, layoutInfo, true);
    }

    /**
     * 레이아웃 바운딩 박스를 이미지에 그리기 (상세 로깅 옵션)
     * @param image 원본 이미지
     * @param layoutInfo 레이아웃 정보 리스트
     * @param enableDetailedLogging 상세 로깅 활성화 여부
     * @return 바운딩 박스가 그려진 이미지
     */
    public BufferedImage drawLayoutBoxes(BufferedImage image, List<LayoutInfo> layoutInfo, boolean enableDetailedLogging) {
        try {
            final int imageWidth = image.getWidth();
            final int imageHeight = image.getHeight();

            logger.info("레이아웃 바운딩 박스 그리기 시작 - 이미지: {}x{}, 요소 수: {}",
                       imageWidth, imageHeight, layoutInfo.size());

            // 좌표 유효성 사전 검증
            int validBoxCount = 0;
            int invalidBoxCount = 0;
            int outOfBoundsCount = 0;
            
            // 이미지 복사
            BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
            Graphics2D g2d = result.createGraphics();
            g2d.drawImage(image, 0, 0, null);
            
            // 안티앨리어싱 및 렌더링 품질 설정
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            
            // 개선된 선 스타일 (더 굵은 테두리로 가독성 향상)
            g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            // 클래스별 고대비 색상 생성 (개선된 HSV 기반)
            var uniqueClasses = layoutInfo.stream()
                .map(LayoutInfo::getClassName)
                .distinct()
                .toList();
            
            // 미리 정의된 고대비 색상 팔레트 (웹 접근성 고려)
            Color[] predefinedColors = {
                new Color(31, 119, 180),   // 파란색
                new Color(255, 127, 14),   // 주황색  
                new Color(44, 160, 44),    // 녹색
                new Color(214, 39, 40),    // 빨간색
                new Color(148, 103, 189),  // 보라색
                new Color(140, 86, 75),    // 갈색
                new Color(227, 119, 194),  // 분홍색
                new Color(127, 127, 127),  // 회색
                new Color(188, 189, 34),   // 올리브색
                new Color(23, 190, 207)    // 청록색
            };
            
            // 각 레이아웃 요소에 대해 바운딩 박스 그리기
            for (int i = 0; i < layoutInfo.size(); i++) {
                LayoutInfo layout = layoutInfo.get(i);
                int[] box = layout.getBox(); // [x1, y1, x2, y2]

                // 1. 박스 배열 유효성 검증
                if (box == null || box.length < 4) {
                    logger.warn("요소 {} ({}) 박스 데이터 무효: {}",
                               i, layout.getClassName(), box != null ? java.util.Arrays.toString(box) : "null");
                    invalidBoxCount++;
                    continue;
                }

                int x1 = box[0], y1 = box[1], x2 = box[2], y2 = box[3];

                // 2. 좌표 유효성 검증 (유틸리티 사용)
                CoordinateScalingUtils.ValidationResult validation =
                    CoordinateScalingUtils.validateCoordinates(box, imageWidth, imageHeight);

                if (!validation.isValid()) {
                    if (enableDetailedLogging) {
                        logger.warn("요소 {} ({}) 좌표 검증 실패: {}",
                                   i, layout.getClassName(), validation.getMessage());
                    }

                    // 좌표 자동 보정 시도
                    int[] correctedBox = CoordinateScalingUtils.clampCoordinates(box, imageWidth, imageHeight);
                    if (correctedBox != null && correctedBox.length >= 4) {
                        x1 = correctedBox[0];
                        y1 = correctedBox[1];
                        x2 = correctedBox[2];
                        y2 = correctedBox[3];
                        outOfBoundsCount++;

                        if (enableDetailedLogging) {
                            logger.info("좌표 자동 보정 완료: [{}, {}, {}, {}]", x1, y1, x2, y2);
                        }
                    } else {
                        invalidBoxCount++;
                        continue;
                    }
                }

                validBoxCount++;
                boolean wasCorrected = outOfBoundsCount > 0;

                if (enableDetailedLogging) {
                    logger.debug("요소 {} 그리기: {} (신뢰도: {:.1f}%) 좌표: [{}, {}, {}, {}] 크기: {}x{}{}",
                               i, layout.getClassName(), layout.getConfidence() * 100,
                               x1, y1, x2, y2, x2-x1, y2-y1, wasCorrected ? " [보정됨]" : "");
                }
                
                // 개선된 클래스별 색상 선택
                int classIndex = uniqueClasses.indexOf(layout.getClassName());
                Color boxColor;
                
                if (classIndex < predefinedColors.length) {
                    // 미리 정의된 색상 사용
                    boxColor = predefinedColors[classIndex];
                } else {
                    // 동적 HSV 색상 생성 (클래스가 많을 경우)
                    float hue = (float) classIndex / Math.max(1, uniqueClasses.size());
                    boxColor = Color.getHSBColor(hue, 0.85f, 0.8f); // 채도와 명도 조정
                }
                
                // 개선된 반투명 채우기 (더 나은 가시성)
                Color fillColor = new Color(boxColor.getRed(), boxColor.getGreen(), boxColor.getBlue(), 40);
                g2d.setColor(fillColor);
                g2d.fillRect(x1, y1, x2 - x1, y2 - y1);
                
                // 강화된 테두리 (더 두꺼운 선으로 명확성 증대)
                g2d.setColor(boxColor);
                g2d.drawRect(x1, y1, x2 - x1, y2 - y1);
                
                // 개선된 라벨 텍스트 (더 큰 폰트와 배경)
                g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14)); // 폰트 크기 증가
                String label = String.format("%s (%.1f%%)", layout.getClassName(), layout.getConfidence() * 100);
                FontMetrics fm = g2d.getFontMetrics();
                int labelWidth = fm.stringWidth(label);
                int labelHeight = fm.getHeight();
                
                // 라벨 위치 계산 (박스 상단 외부에 배치, 화면 내 유지)
                int labelX = Math.max(0, Math.min(x1, imageWidth - labelWidth - 10));
                int labelY = Math.max(labelHeight + 5, y1 - 5);
                
                // 라벨 배경 (더 진한 색상과 패딩으로 가독성 향상)
                Color labelBgColor = new Color(boxColor.getRed(), boxColor.getGreen(), boxColor.getBlue(), 220);
                g2d.setColor(labelBgColor);
                g2d.fillRoundRect(labelX - 5, labelY - labelHeight - 2, labelWidth + 10, labelHeight + 4, 6, 6);
                
                // 라벨 테두리
                g2d.setColor(boxColor.darker());
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawRoundRect(labelX - 5, labelY - labelHeight - 2, labelWidth + 10, labelHeight + 4, 6, 6);
                
                // 라벨 텍스트 (대비를 위해 흰색 또는 검은색 자동 선택)
                Color textColor = getContrastingTextColor(boxColor);
                g2d.setColor(textColor);
                g2d.drawString(label, labelX, labelY - 2);
                
                // 선 굵기 복원
                g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }
            
            g2d.dispose();

            // 결과 요약 로깅
            logger.info("레이아웃 바운딩 박스 그리기 완료 - 유효: {}개, 무효: {}개, 범위초과: {}개",
                       validBoxCount, invalidBoxCount, outOfBoundsCount);

            if (outOfBoundsCount > 0) {
                logger.warn("좌표 범위 초과 요소가 {}개 발견됨. LAM 서비스와 백엔드 이미지 크기 불일치 가능성 검토 필요", outOfBoundsCount);
            }

            return result;
            
        } catch (Exception e) {
            logger.error("레이아웃 바운딩 박스 그리기 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("바운딩 박스 그리기에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 배경 색상에 대비되는 텍스트 색상 선택
     * @param backgroundColor 배경 색상
     * @return 대비가 좋은 텍스트 색상 (흰색 또는 검은색)
     */
    private Color getContrastingTextColor(Color backgroundColor) {
        // 색상의 상대적 명도 계산 (WCAG 2.0 권장)
        double luminance = (0.299 * backgroundColor.getRed() + 
                           0.587 * backgroundColor.getGreen() + 
                           0.114 * backgroundColor.getBlue()) / 255.0;
        
        // 명도가 0.5보다 높으면 검은색, 낮으면 흰색 텍스트
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }
    
    // Inner class for image metadata
    public static class ImageMetadata {
        private int width;
        private int height;
        private int type;
        private String colorModel;
        private boolean hasAlpha;
        
        // Getters and Setters
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        
        public int getType() { return type; }
        public void setType(int type) { this.type = type; }
        
        public String getColorModel() { return colorModel; }
        public void setColorModel(String colorModel) { this.colorModel = colorModel; }
        
        public boolean isHasAlpha() { return hasAlpha; }
        public void setHasAlpha(boolean hasAlpha) { this.hasAlpha = hasAlpha; }
        
        @Override
        public String toString() {
            return String.format("ImageMetadata{width=%d, height=%d, type=%d, colorModel='%s', hasAlpha=%s}",
                    width, height, type, colorModel, hasAlpha);
        }
    }
}
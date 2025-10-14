package com.smarteye.infrastructure.external;

import com.smarteye.presentation.dto.OCRResult;
import com.smarteye.presentation.dto.common.LayoutInfo;
import com.smarteye.shared.exception.FileProcessingException;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OCR 서비스 - Tesseract를 이용한 텍스트 추출
 * Python api_server.py의 perform_ocr() 메서드 변환
 */
@Service
public class OCRService {
    
    private static final Logger logger = LoggerFactory.getLogger(OCRService.class);
    
    private ITesseract tesseract;
    
    @Value("${smarteye.models.tesseract.path:/usr/bin/tesseract}")
    private String tesseractPath;
    
    @Value("${smarteye.models.tesseract.lang:kor+eng}")
    private String tesseractLanguage;
    
    @Value("${smarteye.models.tesseract.datapath:tessdata}")
    private String tesseractDataPath;
    
    // Python 코드에서 가져온 OCR 대상 클래스
    // 🔥 HOTFIX: LAM 서비스에서 공백 포함 클래스명을 반환하므로 두 가지 버전 모두 포함
    private static final Set<String> TARGET_CLASSES = Set.of(
        "title", "plain_text", "abandon_text",
        "table_caption", "table_footnote", "unit", "page",
        "isolated_formula", "formula_caption", 
        "question_type", "question type",  // 공백 버전 추가
        "question_text", "question text",  // 공백 버전 추가
        "question_number", "list"
    );
    
    @PostConstruct
    public void initTesseract() {
        try {
            logger.info("Tesseract 초기화 시작...");
            logger.info("Tesseract DataPath: {}", tesseractDataPath);
            
            // 환경변수 설정 (모든 환경에서 동일하게 작동)
            System.setProperty("TESSDATA_PREFIX", tesseractDataPath);
            
            tesseract = new Tesseract();
            
            // Tesseract 설정 (Python 코드: custom_config = r'--oem 3 --psm 6')
            tesseract.setOcrEngineMode(3); // OEM 3: Default, Legacy + LSTM engines
            tesseract.setPageSegMode(6);   // PSM 6: 균등한 텍스트 블록을 가정
            
            // 언어 설정 (한국어 + 영어)
            tesseract.setLanguage(tesseractLanguage);
            
            // 데이터 경로 설정
            tesseract.setDatapath(tesseractDataPath);
            
            logger.info("Tesseract 초기화 완료 - Language: {}, DataPath: {}", 
                       tesseractLanguage, tesseractDataPath);
                       
        } catch (Exception e) {
            logger.error("Tesseract 초기화 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("Tesseract 초기화에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 단일 이미지에서 텍스트 추출
     * @param image 추출할 이미지
     * @return 추출된 텍스트
     */
    public String extractText(BufferedImage image) {
        try {
            logger.debug("OCR 텍스트 추출 시작 - 이미지 크기: {}x{}", image.getWidth(), image.getHeight());
            
            String result = tesseract.doOCR(image);
            String cleanedText = cleanOCRText(result);
            
            logger.debug("OCR 텍스트 추출 완료 - 텍스트 길이: {}", cleanedText.length());
            return cleanedText;
            
        } catch (TesseractException e) {
            logger.error("OCR 텍스트 추출 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("OCR 텍스트 추출에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 레이아웃 정보를 기반으로 OCR 처리
     * Python 코드의 perform_ocr() 메서드와 동일한 로직
     * @param image 원본 이미지
     * @param layoutInfo 레이아웃 분석 결과
     * @return OCR 결과 리스트
     */
    public List<OCRResult> performOCR(BufferedImage image, List<LayoutInfo> layoutInfo) {
        List<OCRResult> ocrResults = new ArrayList<>();
        
        logger.info("OCR 처리 시작... 총 {}개 레이아웃 요소 중 OCR 대상 필터링", layoutInfo.size());
        logger.info("OCR 대상 클래스 목록: {}", TARGET_CLASSES);
        
        // 감지된 모든 클래스 출력
        Set<String> detectedClasses = layoutInfo.stream()
            .map(layout -> layout.getClassName().toLowerCase())
            .collect(java.util.stream.Collectors.toSet());
        logger.info("감지된 모든 클래스: {}", detectedClasses);
        
        int targetCount = 0;
        
        for (LayoutInfo layout : layoutInfo) {
            String className = layout.getClassName().toLowerCase();
            logger.info("레이아웃 ID {}: 클래스 '{}' 확인 중...", layout.getId(), className);
            
            if (!TARGET_CLASSES.contains(className)) {
                logger.info("  → OCR 대상이 아님 (대상 클래스에 없음)");
                continue;
            }
            
            targetCount++;
            logger.info("  → OCR 대상 {}: ID {} - 클래스 '{}'", targetCount, layout.getId(), className);
            
            // 이미지 크롭
            int[] box = layout.getBox(); // [x1, y1, x2, y2]
            int x1 = Math.max(0, box[0]);
            int y1 = Math.max(0, box[1]);
            int x2 = Math.min(image.getWidth(), box[2]);
            int y2 = Math.min(image.getHeight(), box[3]);
            
            try {
                BufferedImage croppedImg = image.getSubimage(x1, y1, x2 - x1, y2 - y1);
                String text = extractText(croppedImg);

                if (text.length() > 1) {
                    // OCR 신뢰도 계산 (단어별 평균 신뢰도)
                    double confidence = calculateOCRConfidence(croppedImg);

                    OCRResult result = new OCRResult(
                        layout.getId(),
                        className,
                        new int[]{x1, y1, x2, y2},
                        text,
                        confidence
                    );
                    ocrResults.add(result);
                    
                    logger.info("✅ OCR 성공: ID {} ({}) - '{}...' ({}자)", 
                               layout.getId(), className, 
                               text.length() > 50 ? text.substring(0, 50) : text, 
                               text.length());
                } else {
                    logger.warn("⚠️ OCR 결과 없음: ID {} ({})", layout.getId(), className);
                }
                
            } catch (Exception e) {
                logger.error("OCR 실패: ID {} - {}", layout.getId(), e.getMessage(), e);
            }
        }
        
        logger.info("OCR 처리 완료: {}개 텍스트 블록", ocrResults.size());
        return ocrResults;
    }
    
    /**
     * 좌표 정보와 함께 텍스트 추출
     * @param image 추출할 이미지
     * @return 좌표 정보가 포함된 단어 리스트
     */
    public List<Word> extractTextWithCoordinates(BufferedImage image) {
        try {
            logger.debug("OCR 좌표 텍스트 추출 시작");
            
            List<Word> words = tesseract.getWords(image, 1); // RIL_WORD = 1
            
            logger.debug("OCR 좌표 텍스트 추출 완료 - 단어 수: {}", words.size());
            return words;
            
        } catch (Exception e) {
            logger.error("OCR 좌표 텍스트 추출 실패: {}", e.getMessage(), e);
            throw new FileProcessingException("OCR 좌표 텍스트 추출에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * OCR 결과 텍스트 정리
     * @param rawText 원본 OCR 텍스트
     * @return 정리된 텍스트
     */
    private String cleanOCRText(String rawText) {
        if (rawText == null) {
            return "";
        }
        
        return rawText.trim()
                     .replaceAll("\\s+", " ") // 연속된 공백 제거
                     .replaceAll("\\n\\s*\\n", "\n"); // 연속된 빈 줄 제거
    }
    
    /**
     * OCR 대상 클래스인지 확인
     * @param className 클래스명
     * @return OCR 대상 여부
     */
    public boolean isOCRTargetClass(String className) {
        return TARGET_CLASSES.contains(className.toLowerCase());
    }

    /**
     * OCR 신뢰도 계산
     * Tesseract getWords 기능을 사용해 단어별 신뢰도의 평균 계산
     * @param image 이미지
     * @return 0.0~1.0 사이의 신뢰도 값
     */
    private double calculateOCRConfidence(BufferedImage image) {
        try {
            List<Word> words = tesseract.getWords(image, 1); // RIL_WORD = 1

            if (words == null || words.isEmpty()) {
                return 0.8; // 기본값: 단어가 없으면 적당한 신뢰도
            }

            // 신뢰도가 0보다 큰 단어들의 평균 계산
            double totalConfidence = 0.0;
            int validWordCount = 0;

            for (Word word : words) {
                float wordConfidence = word.getConfidence();
                if (wordConfidence > 0) { // 0 이상의 신뢰도만 고려
                    totalConfidence += wordConfidence;
                    validWordCount++;
                }
            }

            if (validWordCount == 0) {
                return 0.5; // 유효한 단어가 없으면 중간 신뢰도
            }

            // Tesseract confidence는 0-100 범위이므로 0-1로 변환
            double averageConfidence = (totalConfidence / validWordCount) / 100.0;

            // 0.0 ~ 1.0 범위로 제한
            return Math.max(0.0, Math.min(1.0, averageConfidence));

        } catch (Exception e) {
            logger.warn("OCR 신뢰도 계산 실패: {}", e.getMessage());
            return 0.8; // 오류 시 기본값
        }
    }
    
}
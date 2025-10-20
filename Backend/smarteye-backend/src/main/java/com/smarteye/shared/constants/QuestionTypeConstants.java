package com.smarteye.shared.constants;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * v0.7 Question Type 관련 상수 및 유틸리티 클래스.
 *
 * <p>이 클래스는 question_type 독립 영역 처리를 위한 ID 생성, 검증, 파싱 유틸리티를 제공합니다.</p>
 *
 * <h2>ID 포맷</h2>
 * <pre>
 * type_{layoutId}_{sanitizedText}
 * 예시: type_5_유형01, type_12_문제유형A
 * </pre>
 *
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>question_type ID 생성 (길이 제한 + Hash suffix)</li>
 *   <li>question_type ID 검증</li>
 *   <li>Layout ID 추출</li>
 *   <li>OCR 텍스트 정제 (Sanitization)</li>
 * </ul>
 *
 * @author SmartEye Development Team
 * @version 0.7
 * @since 2025-10-18
 */
public final class QuestionTypeConstants {

    // ============================================================
    // 상수 정의
    // ============================================================

    /**
     * question_type ID 접두사.
     * 모든 question_type ID는 이 접두사로 시작합니다.
     */
    public static final String PREFIX = "type_";

    /**
     * OCR 텍스트 최대 길이 (hash suffix 제외).
     * 이 길이를 초과하면 hash suffix가 추가됩니다.
     */
    public static final int MAX_TEXT_LENGTH = 50;

    /**
     * Hash suffix 길이.
     * 긴 텍스트의 경우 MD5 해시의 앞 8자를 suffix로 사용합니다.
     */
    private static final int HASH_SUFFIX_LENGTH = 8;

    /**
     * question_type ID 검증용 정규식 패턴.
     * 형식: type_{숫자}_{텍스트}
     */
    private static final Pattern TYPE_ID_PATTERN = Pattern.compile("^type_\\d+_.+$");

    // ============================================================
    // Private 생성자 (인스턴스화 방지)
    // ============================================================

    private QuestionTypeConstants() {
        throw new AssertionError("QuestionTypeConstants는 인스턴스화할 수 없습니다.");
    }

    // ============================================================
    // 공개 메서드
    // ============================================================

    /**
     * question_type ID를 생성합니다.
     *
     * <p>생성 규칙:</p>
     * <ol>
     *   <li>OCR 텍스트를 정제 (띄어쓰기 → 언더스코어, 특수문자 제거)</li>
     *   <li>길이가 {@link #MAX_TEXT_LENGTH}를 초과하면 잘라내고 hash suffix 추가</li>
     *   <li>{@link #PREFIX}{layoutId}_{정제된텍스트} 형식으로 반환</li>
     * </ol>
     *
     * <h3>예시</h3>
     * <pre>
     * generateIdentifier(5, "유형 01")         → "type_5_유형_01"
     * generateIdentifier(12, "문제 유형 A")    → "type_12_문제_유형_A"
     * generateIdentifier(7, "매우_긴_텍스트_...") → "type_7_매우_긴_텍스트_..._a1b2c3d4"
     * </pre>
     *
     * @param layoutId LAM 레이아웃 ID
     * @param ocrText OCR 추출 텍스트
     * @return 생성된 question_type ID
     * @throws IllegalArgumentException OCR 텍스트가 null이거나 비어있을 때
     */
    public static String generateIdentifier(int layoutId, String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "OCR 텍스트가 null이거나 비어있습니다. layoutId=" + layoutId
            );
        }

        // 1. OCR 텍스트 정제
        String sanitized = sanitizeText(ocrText);

        // 2. 길이 체크 및 hash suffix 추가 (필요시)
        if (sanitized.length() > MAX_TEXT_LENGTH) {
            String truncated = sanitized.substring(0, MAX_TEXT_LENGTH);
            String hashSuffix = generateHashSuffix(sanitized);
            sanitized = truncated + "_" + hashSuffix;
        }

        // 3. ID 생성
        return PREFIX + layoutId + "_" + sanitized;
    }

    /**
     * 주어진 문자열이 question_type ID인지 검증합니다.
     *
     * <h3>검증 규칙</h3>
     * <ul>
     *   <li>{@link #PREFIX}로 시작</li>
     *   <li>형식: type_{숫자}_{텍스트}</li>
     * </ul>
     *
     * @param identifier 검증할 문자열
     * @return question_type ID이면 true, 아니면 false
     */
    public static boolean isQuestionTypeIdentifier(String identifier) {
        if (identifier == null) {
            return false;
        }
        return identifier.startsWith(PREFIX) && TYPE_ID_PATTERN.matcher(identifier).matches();
    }

    /**
     * question_type ID에서 Layout ID를 추출합니다.
     *
     * <h3>추출 예시</h3>
     * <pre>
     * parseLayoutId("type_5_유형01")  → 5
     * parseLayoutId("type_12_문제A")   → 12
     * </pre>
     *
     * @param identifier question_type ID
     * @return Layout ID
     * @throws IllegalArgumentException 유효하지 않은 ID 형식
     */
    public static int parseLayoutId(String identifier) {
        if (!isQuestionTypeIdentifier(identifier)) {
            throw new IllegalArgumentException(
                "유효하지 않은 question_type ID: " + identifier
            );
        }

        try {
            // "type_5_유형01" → ["type", "5", "유형01"]
            String[] parts = identifier.split("_", 3);
            return Integer.parseInt(parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Layout ID 파싱 실패: " + identifier, e
            );
        }
    }

    /**
     * question_type ID에서 원본 텍스트 부분을 추출합니다.
     *
     * <h3>추출 예시</h3>
     * <pre>
     * extractText("type_5_유형01")  → "유형01"
     * extractText("type_12_문제A")   → "문제A"
     * </pre>
     *
     * @param identifier question_type ID
     * @return 텍스트 부분 (언더스코어 포함)
     * @throws IllegalArgumentException 유효하지 않은 ID 형식
     */
    public static String extractText(String identifier) {
        if (!isQuestionTypeIdentifier(identifier)) {
            throw new IllegalArgumentException(
                "유효하지 않은 question_type ID: " + identifier
            );
        }

        // "type_5_유형01" → "유형01"
        int secondUnderscoreIndex = identifier.indexOf('_', PREFIX.length());
        if (secondUnderscoreIndex == -1) {
            throw new IllegalArgumentException(
                "텍스트 부분이 없는 ID: " + identifier
            );
        }

        return identifier.substring(secondUnderscoreIndex + 1);
    }

    // ============================================================
    // 내부 유틸리티 메서드
    // ============================================================

    /**
     * OCR 텍스트를 정제합니다.
     *
     * <h3>정제 규칙</h3>
     * <ol>
     *   <li>앞뒤 공백 제거</li>
     *   <li>연속 공백 → 단일 언더스코어</li>
     *   <li>특수문자 제거 (한글, 영문, 숫자, 언더스코어만 허용)</li>
     * </ol>
     *
     * @param text 원본 텍스트
     * @return 정제된 텍스트
     */
    private static String sanitizeText(String text) {
        return text.trim()
            .replaceAll("\\s+", "_")                       // 공백 → 언더스코어
            .replaceAll("[^가-힣a-zA-Z0-9_]", "");         // 특수문자 제거
    }

    /**
     * 텍스트의 MD5 해시 앞 8자를 반환합니다.
     *
     * <p>긴 텍스트의 고유성을 보장하기 위해 사용됩니다.</p>
     *
     * @param text 해시할 텍스트
     * @return MD5 해시 앞 8자 (소문자)
     */
    private static String generateHashSuffix(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(text.getBytes());
            
            // byte[] → hex string 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // 앞 8자만 반환
            return hexString.substring(0, HASH_SUFFIX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // MD5는 항상 사용 가능하므로 이 예외는 발생하지 않음
            throw new RuntimeException("MD5 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    /**
     * 전각 문자를 반각 문자로 정규화합니다.
     *
     * <p>v0.7 P1 Fix: 한국어 학습지 대응</p>
     * <ul>
     *   <li>전각 괄호: （ → (, ） → )</li>
     *   <li>전각 숫자: ０-９ → 0-9</li>
     * </ul>
     *
     * <h3>예시</h3>
     * <pre>
     * normalizeFullWidthCharacters("（１）") → "(1)"
     * normalizeFullWidthCharacters("（２）") → "(2)"
     * normalizeFullWidthCharacters("유형０１") → "유형01"
     * </pre>
     *
     * @param text 정규화할 텍스트
     * @return 정규화된 텍스트
     */
    public static String normalizeFullWidthCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 전각 괄호 → 반각
        String normalized = text
            .replaceAll("（", "(")
            .replaceAll("）", ")");

        // 전각 숫자 → 반각 (０-９ → 0-9)
        StringBuilder result = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (c >= '０' && c <= '９') {
                // 전각 숫자를 반각으로 변환
                result.append((char) (c - '０' + '0'));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 디버깅용 문자열 표현.
     *
     * @param identifier question_type ID
     * @return 디버깅 정보
     */
    public static String toDebugString(String identifier) {
        if (!isQuestionTypeIdentifier(identifier)) {
            return "[유효하지 않은 ID] " + identifier;
        }

        try {
            int layoutId = parseLayoutId(identifier);
            String text = extractText(identifier);
            return String.format(
                "[question_type] Layout ID=%d, Text='%s'",
                layoutId, text
            );
        } catch (Exception e) {
            return "[파싱 실패] " + identifier + " - " + e.getMessage();
        }
    }
}

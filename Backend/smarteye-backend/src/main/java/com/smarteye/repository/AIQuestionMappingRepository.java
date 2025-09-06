package com.smarteye.repository;

import com.smarteye.entity.AIQuestionMapping;
import com.smarteye.entity.CIMOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIQuestionMappingRepository extends JpaRepository<AIQuestionMapping, Long> {
    
    /**
     * CIM Output으로 AI-문제 매핑 목록 조회 (문제 번호 순서대로)
     */
    List<AIQuestionMapping> findByCimOutputOrderByQuestionNumberAsc(CIMOutput cimOutput);
    
    /**
     * CIM Output으로 AI-문제 매핑 목록 조회 (거리 점수 순서대로)
     */
    List<AIQuestionMapping> findByCimOutputOrderByDistanceScoreAsc(CIMOutput cimOutput);
    
    /**
     * 특정 문제 번호의 AI 매핑들 조회
     */
    List<AIQuestionMapping> findByCimOutputAndQuestionNumberOrderByDistanceScoreAsc(CIMOutput cimOutput, String questionNumber);
    
    /**
     * 신뢰도별 AI 매핑 조회
     */
    List<AIQuestionMapping> findByCimOutputAndConfidenceLevel(CIMOutput cimOutput, AIQuestionMapping.ConfidenceLevel confidenceLevel);
    
    /**
     * 높은 신뢰도 매핑들만 조회 (HIGH + MEDIUM)
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.confidenceLevel IN ('HIGH', 'MEDIUM')")
    List<AIQuestionMapping> findByCimOutputWithHighConfidence(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 특정 요소 클래스의 매핑들 조회
     */
    List<AIQuestionMapping> findByCimOutputAndElementClass(CIMOutput cimOutput, String elementClass);
    
    /**
     * 이미지 관련 매핑들 조회
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.elementClass IN ('figure', 'image')")
    List<AIQuestionMapping> findByCimOutputWithImages(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 테이블 관련 매핑들 조회
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.elementClass = 'table'")
    List<AIQuestionMapping> findByCimOutputWithTables(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 수식 관련 매핑들 조회
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.elementClass IN ('formula', 'isolated_formula')")
    List<AIQuestionMapping> findByCimOutputWithFormulas(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 거리 점수 범위로 매핑 조회
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.distanceScore BETWEEN :minDistance AND :maxDistance")
    List<AIQuestionMapping> findByCimOutputInDistanceRange(@Param("cimOutput") CIMOutput cimOutput, @Param("minDistance") Integer minDistance, @Param("maxDistance") Integer maxDistance);
    
    /**
     * 500px 이내 매핑들 조회 (Python 기준)
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.distanceScore <= 500")
    List<AIQuestionMapping> findByCimOutputWithin500px(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 특정 요소 ID와 연결된 매핑 조회
     */
    Optional<AIQuestionMapping> findByCimOutputAndElementId(CIMOutput cimOutput, Long elementId);
    
    /**
     * 매핑 방법별 조회
     */
    List<AIQuestionMapping> findByCimOutputAndMappingMethod(CIMOutput cimOutput, String mappingMethod);
    
    /**
     * Y좌표 범위로 매핑 조회
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.yCoordinate BETWEEN :minY AND :maxY")
    List<AIQuestionMapping> findByCimOutputInYRange(@Param("cimOutput") CIMOutput cimOutput, @Param("minY") Integer minY, @Param("maxY") Integer maxY);
    
    /**
     * AI 설명 텍스트로 검색 (부분 매칭)
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.aiDescription LIKE %:keyword%")
    List<AIQuestionMapping> findByCimOutputWithDescriptionContaining(@Param("cimOutput") CIMOutput cimOutput, @Param("keyword") String keyword);
    
    /**
     * 문제별 매핑 개수 조회
     */
    @Query("SELECT aim.questionNumber, COUNT(aim) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput GROUP BY aim.questionNumber")
    List<Object[]> getMappingCountByQuestion(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 요소 클래스별 매핑 개수 조회
     */
    @Query("SELECT aim.elementClass, COUNT(aim) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput GROUP BY aim.elementClass")
    List<Object[]> getMappingCountByElementClass(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 신뢰도별 매핑 개수 조회
     */
    @Query("SELECT aim.confidenceLevel, COUNT(aim) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput GROUP BY aim.confidenceLevel")
    List<Object[]> getMappingCountByConfidenceLevel(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * CIM Output의 전체 매핑 개수 조회
     */
    @Query("SELECT COUNT(aim) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput")
    Long countByCimOutput(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 특정 문제 번호의 매핑 개수 조회
     */
    @Query("SELECT COUNT(aim) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.questionNumber = :questionNumber")
    Long countByCimOutputAndQuestionNumber(@Param("cimOutput") CIMOutput cimOutput, @Param("questionNumber") String questionNumber);
    
    /**
     * 높은 신뢰도 매핑 개수 조회
     */
    @Query("SELECT COUNT(aim) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.confidenceLevel IN ('HIGH', 'MEDIUM')")
    Long countByCimOutputWithHighConfidence(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 평균 거리 점수 조회
     */
    @Query("SELECT AVG(aim.distanceScore) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.distanceScore IS NOT NULL")
    Double getAverageDistanceScore(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 최소 거리 점수 조회
     */
    @Query("SELECT MIN(aim.distanceScore) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.distanceScore IS NOT NULL")
    Integer getMinDistanceScore(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 최대 거리 점수 조회
     */
    @Query("SELECT MAX(aim.distanceScore) FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.distanceScore IS NOT NULL")
    Integer getMaxDistanceScore(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 특정 문제와 가장 가까운 매핑 조회
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.questionNumber = :questionNumber ORDER BY aim.distanceScore ASC")
    List<AIQuestionMapping> findClosestMappingsByQuestion(@Param("cimOutput") CIMOutput cimOutput, @Param("questionNumber") String questionNumber);
    
    /**
     * 특정 요소 클래스와 문제 번호의 매핑 존재 여부 확인
     */
    boolean existsByCimOutputAndQuestionNumberAndElementClass(CIMOutput cimOutput, String questionNumber, String elementClass);
    
    /**
     * 중복 매핑 조회 (같은 요소 ID를 가진 매핑들)
     */
    @Query("SELECT aim FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.elementId IN (SELECT aim2.elementId FROM AIQuestionMapping aim2 WHERE aim2.cimOutput = :cimOutput GROUP BY aim2.elementId HAVING COUNT(aim2) > 1)")
    List<AIQuestionMapping> findDuplicateMappings(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * AI-문제 매핑 삭제 (CIM Output 기준)
     */
    void deleteByCimOutput(CIMOutput cimOutput);
    
    /**
     * 특정 문제 번호의 매핑 삭제
     */
    void deleteByCimOutputAndQuestionNumber(CIMOutput cimOutput, String questionNumber);
    
    /**
     * 낮은 신뢰도 매핑 삭제
     */
    @Query("DELETE FROM AIQuestionMapping aim WHERE aim.cimOutput = :cimOutput AND aim.confidenceLevel = 'LOW'")
    void deleteByCimOutputWithLowConfidence(@Param("cimOutput") CIMOutput cimOutput);
}
package com.smarteye.repository;

import com.smarteye.entity.QuestionStructure;
import com.smarteye.entity.CIMOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionStructureRepository extends JpaRepository<QuestionStructure, Long> {
    
    /**
     * CIM Output으로 문제 구조 목록 조회 (문제 번호 순서대로)
     */
    List<QuestionStructure> findByCimOutputOrderByQuestionNumberAsc(CIMOutput cimOutput);
    
    /**
     * CIM Output으로 문제 구조 목록 조회 (Y좌표 순서대로)
     */
    List<QuestionStructure> findByCimOutputOrderByStartYAsc(CIMOutput cimOutput);
    
    /**
     * 특정 문제 번호의 구조 조회
     */
    Optional<QuestionStructure> findByCimOutputAndQuestionNumber(CIMOutput cimOutput, String questionNumber);
    
    /**
     * 섹션별 문제 구조 목록 조회
     */
    List<QuestionStructure> findByCimOutputAndSectionNameOrderByQuestionNumberAsc(CIMOutput cimOutput, String sectionName);
    
    /**
     * 선택지가 있는 문제들 조회
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.choicesCount > 0")
    List<QuestionStructure> findByCimOutputWithChoices(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 이미지가 있는 문제들 조회
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.imagesCount > 0")
    List<QuestionStructure> findByCimOutputWithImages(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 테이블이 있는 문제들 조회
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.tablesCount > 0")
    List<QuestionStructure> findByCimOutputWithTables(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 신뢰도 점수가 특정 값 이상인 문제들 조회
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.confidenceScore >= :minScore")
    List<QuestionStructure> findByCimOutputWithMinConfidence(@Param("cimOutput") CIMOutput cimOutput, @Param("minScore") Double minScore);
    
    /**
     * Y좌표 범위로 문제 구조 조회
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.startY >= :minY AND qs.endY <= :maxY")
    List<QuestionStructure> findByCimOutputInYRange(@Param("cimOutput") CIMOutput cimOutput, @Param("minY") Integer minY, @Param("maxY") Integer maxY);
    
    /**
     * 특정 Y좌표에 포함되는 문제 구조 조회
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.startY <= :y AND qs.endY >= :y")
    Optional<QuestionStructure> findByCimOutputContainingY(@Param("cimOutput") CIMOutput cimOutput, @Param("y") Integer y);
    
    /**
     * CIM Output의 전체 문제 개수 조회
     */
    @Query("SELECT COUNT(qs) FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput")
    Long countByCimOutput(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 섹션별 문제 개수 조회
     */
    @Query("SELECT COUNT(qs) FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.sectionName = :sectionName")
    Long countByCimOutputAndSectionName(@Param("cimOutput") CIMOutput cimOutput, @Param("sectionName") String sectionName);
    
    /**
     * CIM Output과 문제 번호 존재 여부 확인
     */
    boolean existsByCimOutputAndQuestionNumber(CIMOutput cimOutput, String questionNumber);
    
    /**
     * 유효한 범위를 가진 문제 구조들 조회
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.startY IS NOT NULL AND qs.endY IS NOT NULL AND qs.endY > qs.startY")
    List<QuestionStructure> findByCimOutputWithValidRange(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 문제 번호로 검색 (부분 매칭)
     */
    @Query("SELECT qs FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.questionNumber LIKE %:numberPattern%")
    List<QuestionStructure> findByCimOutputAndQuestionNumberContaining(@Param("cimOutput") CIMOutput cimOutput, @Param("numberPattern") String numberPattern);
    
    /**
     * 모든 섹션 이름 조회 (중복 제거)
     */
    @Query("SELECT DISTINCT qs.sectionName FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput AND qs.sectionName IS NOT NULL")
    List<String> findDistinctSectionNamesByCimOutput(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 통계 정보 조회 - 섹션별 문제 수
     */
    @Query("SELECT qs.sectionName, COUNT(qs) FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput GROUP BY qs.sectionName")
    List<Object[]> getQuestionCountBySection(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 통계 정보 조회 - 요소별 평균 개수
     */
    @Query("SELECT AVG(qs.choicesCount), AVG(qs.imagesCount), AVG(qs.tablesCount) FROM QuestionStructure qs WHERE qs.cimOutput = :cimOutput")
    Object[] getAverageElementCounts(@Param("cimOutput") CIMOutput cimOutput);
    
    /**
     * 문제 구조 삭제 (CIM Output 기준)
     */
    void deleteByCimOutput(CIMOutput cimOutput);
    
    /**
     * 특정 문제 번호 삭제
     */
    void deleteByCimOutputAndQuestionNumber(CIMOutput cimOutput, String questionNumber);
}
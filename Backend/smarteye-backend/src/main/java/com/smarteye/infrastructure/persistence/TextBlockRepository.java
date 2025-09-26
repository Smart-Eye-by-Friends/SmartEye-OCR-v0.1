package com.smarteye.infrastructure.persistence;

import com.smarteye.domain.analysis.TextBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TextBlockRepository extends JpaRepository<TextBlock, Long> {
    
    Optional<TextBlock> findByLayoutBlockId(Long layoutBlockId);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.layoutBlock.documentPage.analysisJob.jobId = :jobId")
    List<TextBlock> findByJobId(@Param("jobId") String jobId);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.layoutBlock.documentPage.id = :pageId")
    List<TextBlock> findByDocumentPageId(@Param("pageId") Long pageId);
    
    List<TextBlock> findByTextType(TextBlock.TextType textType);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.textType IN :textTypes")
    List<TextBlock> findByTextTypeIn(@Param("textTypes") List<TextBlock.TextType> textTypes);
    
    List<TextBlock> findByLanguage(String language);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.confidence >= :minConfidence")
    List<TextBlock> findByConfidenceGreaterThanEqual(@Param("minConfidence") Double minConfidence);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.confidence < :threshold")
    List<TextBlock> findLowConfidenceTexts(@Param("threshold") Double threshold);
    
    // Text content queries
    @Query("SELECT tb FROM TextBlock tb WHERE tb.extractedText LIKE %:text%")
    List<TextBlock> findByExtractedTextContaining(@Param("text") String text);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.cleanedText LIKE %:text%")
    List<TextBlock> findByCleanedTextContaining(@Param("text") String text);
    
    @Query("SELECT tb FROM TextBlock tb WHERE LENGTH(tb.extractedText) = 0 OR tb.extractedText IS NULL")
    List<TextBlock> findEmptyTextBlocks();
    
    @Query("SELECT tb FROM TextBlock tb WHERE LENGTH(tb.extractedText) > 0")
    List<TextBlock> findNonEmptyTextBlocks();
    
    // Word and character count queries
    @Query("SELECT tb FROM TextBlock tb WHERE tb.wordCount >= :minWords")
    List<TextBlock> findByMinWordCount(@Param("minWords") Integer minWords);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.charCount >= :minChars")
    List<TextBlock> findByMinCharCount(@Param("minChars") Integer minChars);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.wordCount BETWEEN :minWords AND :maxWords")
    List<TextBlock> findByWordCountBetween(@Param("minWords") Integer minWords, @Param("maxWords") Integer maxWords);
    
    // Font and formatting queries
    @Query("SELECT tb FROM TextBlock tb WHERE tb.fontSize >= :minSize")
    List<TextBlock> findByMinFontSize(@Param("minSize") Integer minSize);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.isBold = true")
    List<TextBlock> findBoldTexts();
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.isItalic = true")
    List<TextBlock> findItalicTexts();
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.isBold = true OR tb.isItalic = true")
    List<TextBlock> findFormattedTexts();
    
    @Query("SELECT tb FROM TextBlock tb WHERE ABS(tb.textAngle) > :minAngle")
    List<TextBlock> findRotatedTexts(@Param("minAngle") Double minAngle);
    
    // Statistics queries
    @Query("SELECT COUNT(tb) FROM TextBlock tb WHERE tb.textType = :textType")
    long countByTextType(@Param("textType") TextBlock.TextType textType);
    
    @Query("SELECT COUNT(tb) FROM TextBlock tb WHERE tb.language = :language")
    long countByLanguage(@Param("language") String language);
    
    @Query("SELECT AVG(tb.confidence) FROM TextBlock tb WHERE tb.confidence IS NOT NULL")
    Double getAverageConfidence();
    
    @Query("SELECT AVG(tb.wordCount) FROM TextBlock tb WHERE tb.wordCount IS NOT NULL")
    Double getAverageWordCount();
    
    @Query("SELECT AVG(tb.charCount) FROM TextBlock tb WHERE tb.charCount IS NOT NULL")
    Double getAverageCharCount();
    
    @Query("SELECT SUM(tb.wordCount) FROM TextBlock tb WHERE tb.layoutBlock.documentPage.analysisJob.jobId = :jobId")
    Long getTotalWordCountByJobId(@Param("jobId") String jobId);
    
    @Query("SELECT SUM(tb.charCount) FROM TextBlock tb WHERE tb.layoutBlock.documentPage.analysisJob.jobId = :jobId")
    Long getTotalCharCountByJobId(@Param("jobId") String jobId);
    
    // Complex queries with joins
    @Query("""
        SELECT tb FROM TextBlock tb 
        JOIN tb.layoutBlock lb 
        WHERE lb.className = :className
        """)
    List<TextBlock> findByLayoutBlockClassName(@Param("className") String className);
    
    @Query("""
        SELECT tb FROM TextBlock tb 
        JOIN tb.layoutBlock lb 
        WHERE lb.className IN :classNames
        """)
    List<TextBlock> findByLayoutBlockClassNameIn(@Param("classNames") List<String> classNames);
    
    @Query("""
        SELECT tb FROM TextBlock tb 
        JOIN tb.layoutBlock lb 
        JOIN lb.documentPage dp 
        WHERE dp.analysisJob.jobId = :jobId 
        AND dp.pageNumber = :pageNumber
        """)
    List<TextBlock> findByJobIdAndPageNumber(@Param("jobId") String jobId, @Param("pageNumber") Integer pageNumber);
    
    // Content analysis queries
    @Query("""
        SELECT tb FROM TextBlock tb 
        WHERE tb.extractedText LIKE :pattern
        """)
    List<TextBlock> findByTextPattern(@Param("pattern") String pattern);
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.extractedText LIKE '%1.%' OR tb.extractedText LIKE '%2.%' OR tb.extractedText LIKE '%3.%' OR tb.extractedText LIKE '%4.%' OR tb.extractedText LIKE '%5.%' OR tb.extractedText LIKE '%6.%' OR tb.extractedText LIKE '%7.%' OR tb.extractedText LIKE '%8.%' OR tb.extractedText LIKE '%9.%' OR tb.extractedText LIKE '%0.%'")
    List<TextBlock> findNumberedItems();
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.extractedText LIKE '%가%' OR tb.extractedText LIKE '%각%' OR tb.extractedText LIKE '%나%' OR tb.extractedText LIKE '%다%' OR tb.extractedText LIKE '%라%' OR tb.extractedText LIKE '%마%' OR tb.extractedText LIKE '%바%' OR tb.extractedText LIKE '%사%' OR tb.extractedText LIKE '%아%' OR tb.extractedText LIKE '%자%' OR tb.extractedText LIKE '%차%' OR tb.extractedText LIKE '%카%' OR tb.extractedText LIKE '%타%' OR tb.extractedText LIKE '%파%' OR tb.extractedText LIKE '%하%'")
    List<TextBlock> findKoreanTexts();
    
    @Query("SELECT tb FROM TextBlock tb WHERE UPPER(tb.extractedText) != LOWER(tb.extractedText)")
    List<TextBlock> findEnglishTexts();
    
    @Query("SELECT tb FROM TextBlock tb WHERE tb.extractedText LIKE '%0%' OR tb.extractedText LIKE '%1%' OR tb.extractedText LIKE '%2%' OR tb.extractedText LIKE '%3%' OR tb.extractedText LIKE '%4%' OR tb.extractedText LIKE '%5%' OR tb.extractedText LIKE '%6%' OR tb.extractedText LIKE '%7%' OR tb.extractedText LIKE '%8%' OR tb.extractedText LIKE '%9%'")
    List<TextBlock> findTextsWithNumbers();
    
    // Quality assessment queries
    @Query("""
        SELECT tb FROM TextBlock tb 
        WHERE tb.confidence >= :highThreshold
        """)
    List<TextBlock> findHighQualityTexts(@Param("highThreshold") Double highThreshold);
    
    @Query("""
        SELECT tb FROM TextBlock tb 
        WHERE tb.confidence < :lowThreshold 
        AND LENGTH(tb.extractedText) > :minLength
        """)
    List<TextBlock> findSuspiciousLowQualityTexts(@Param("lowThreshold") Double lowThreshold, 
                                                 @Param("minLength") Integer minLength);
    
    // Aggregation queries
    @Query("SELECT tb.textType, COUNT(tb), AVG(tb.confidence), AVG(tb.wordCount) FROM TextBlock tb GROUP BY tb.textType")
    List<Object[]> getTextTypeStatistics();
    
    @Query("SELECT tb.language, COUNT(tb), AVG(tb.confidence) FROM TextBlock tb GROUP BY tb.language")
    List<Object[]> getLanguageStatistics();
}
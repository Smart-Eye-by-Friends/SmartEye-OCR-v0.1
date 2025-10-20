package com.smarteye.application.analysis.finder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BoundaryElementFinder 팩토리 클래스.
 *
 * <p>이 클래스는 문제 식별자에 맞는 적절한 Finder를 반환합니다.</p>
 *
 * <h2>지원하는 Finder</h2>
 * <ul>
 *   <li>{@link QuestionNumberElementFinder}: question_number 처리</li>
 *   <li>{@link QuestionTypeElementFinder}: question_type(type_*) 처리</li>
 * </ul>
 *
 * <h2>사용 예제</h2>
 * <pre>{@code
 * // Spring DI를 통한 주입
 * @Autowired
 * private BoundaryElementFinderFactory finderFactory;
 *
 * // Finder 획득
 * BoundaryElementFinder finder = finderFactory.getFinder("003");         // QuestionNumberElementFinder
 * BoundaryElementFinder finder = finderFactory.getFinder("type_5_유형01"); // QuestionTypeElementFinder
 * }</pre>
 *
 * @author SmartEye Development Team
 * @version 0.7
 * @since 2025-10-18
 */
@Component
public class BoundaryElementFinderFactory {

    private static final Logger logger = LoggerFactory.getLogger(BoundaryElementFinderFactory.class);

    private final List<BoundaryElementFinder> finders;

    /**
     * 생성자 (Spring DI를 통한 주입).
     *
     * @param finders 등록된 모든 BoundaryElementFinder 구현체
     */
    @Autowired
    public BoundaryElementFinderFactory(List<BoundaryElementFinder> finders) {
        this.finders = finders;
        logger.info("🔍 BoundaryElementFinderFactory 초기화: {}개 Finder 등록", finders.size());
        
        // 디버깅: 등록된 Finder 로깅
        for (BoundaryElementFinder finder : finders) {
            logger.debug("  - {}", finder.getClass().getSimpleName());
        }
    }

    /**
     * 문제 식별자에 맞는 Finder를 반환합니다.
     *
     * <h3>선택 로직</h3>
     * <ol>
     *   <li>등록된 모든 Finder에 대해 {@link BoundaryElementFinder#supports(String)} 호출</li>
     *   <li>첫 번째로 true를 반환하는 Finder 선택</li>
     *   <li>지원하는 Finder가 없으면 IllegalArgumentException 발생</li>
     * </ol>
     *
     * @param questionIdentifier 문제 식별자 ("003" 또는 "type_5_유형01")
     * @return 적절한 BoundaryElementFinder
     * @throws IllegalArgumentException 지원하는 Finder가 없을 때
     */
    public BoundaryElementFinder getFinder(String questionIdentifier) {
        for (BoundaryElementFinder finder : finders) {
            if (finder.supports(questionIdentifier)) {
                logger.trace("🎯 Finder 선택: {} → {}",
                           questionIdentifier, finder.getClass().getSimpleName());
                return finder;
            }
        }

        // 지원하는 Finder가 없음 (이론상 발생하지 않아야 함)
        throw new IllegalArgumentException(
            "지원하지 않는 문제 식별자 형식: " + questionIdentifier +
            " (등록된 Finder: " + finders.size() + "개)"
        );
    }

    /**
     * 등록된 Finder 개수를 반환합니다.
     *
     * @return Finder 개수
     */
    public int getFinderCount() {
        return finders.size();
    }
}

// src/components/editor/AIStatsTab.tsx
import React, { useMemo } from "react";
import styles from "./AIStatsTab.module.css";

const AIStatsTab: React.FC = () => {
  // TODO: 실제 데이터 연동
  const analysisResult = {
    totalElements: 38,
    questionCount: 5,
    processingTime: 2.5,
    classDistribution: {
      question_number: 5,
      question_text: 5,
      choices: 15,
      figure: 3,
      table: 1,
    },
    confidenceScores: {
      question_number: 0.95,
      question_text: 0.92,
      choices: 0.88,
      figure: 0.85,
      table: 0.9,
    },
  };

  const statCards = useMemo(
    () => [
      {
        icon: "📊",
        label: "총 요소 개수",
        value: analysisResult.totalElements,
        color: "#2196F3",
      },
      {
        icon: "❓",
        label: "문제 개수",
        value: analysisResult.questionCount,
        color: "#4CAF50",
      },
      {
        icon: "⏱️",
        label: "처리 시간",
        value: `${analysisResult.processingTime}초`,
        color: "#FF9800",
      },
    ],
    [analysisResult]
  );

  const distributionData = useMemo(() => {
    const entries = Object.entries(analysisResult.classDistribution);
    const maxCount = Math.max(...entries.map(([, count]) => count as number));

    return entries.map(([className, count]) => ({
      className,
      count,
      percentage: ((count as number) / maxCount) * 100,
    }));
  }, [analysisResult]);

  return (
    <div className={styles.aiStatsTab}>
      {/* 통계 카드 */}
      <div className={styles.statCards}>
        {statCards.map((card) => (
          <div
            key={card.label}
            className={styles.statCard}
            style={{ borderColor: card.color }}
          >
            <div className={styles.cardIcon} style={{ color: card.color }}>
              {card.icon}
            </div>
            <div className={styles.cardContent}>
              <div className={styles.cardValue}>{card.value}</div>
              <div className={styles.cardLabel}>{card.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* 클래스별 분포 */}
      <div className={styles.classDistribution}>
        <h3>클래스별 분포</h3>
        <div className={styles.distributionBars}>
          {distributionData.map((item) => (
            <div key={item.className} className={styles.distributionItem}>
              <div className={styles.itemLabel}>{item.className}</div>
              <div className={styles.barContainer}>
                <div
                  className={styles.barFill}
                  style={{ width: `${item.percentage}%` }}
                />
                <span className={styles.barValue}>{item.count}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 상세 정보 테이블 */}
      <div className={styles.detailTable}>
        <h3>상세 정보</h3>
        <table>
          <thead>
            <tr>
              <th>클래스</th>
              <th>개수</th>
              <th>평균 신뢰도</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(analysisResult.classDistribution).map(
              ([className, count]) => (
                <tr key={className}>
                  <td>{className}</td>
                  <td>{count}</td>
                  <td>
                    {(
                      analysisResult.confidenceScores[
                        className as keyof typeof analysisResult.confidenceScores
                      ] * 100
                    ).toFixed(1)}
                    %
                  </td>
                </tr>
              )
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AIStatsTab;

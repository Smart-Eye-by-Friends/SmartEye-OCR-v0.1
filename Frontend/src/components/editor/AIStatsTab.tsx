// src/components/editor/AIStatsTab.tsx
import React, { useEffect, useMemo, useState } from "react";
import styles from "./AIStatsTab.module.css";
import { analysisService } from "@/services/analysis";
import type { PageStatsResponse } from "@/services/analysis";

interface AIStatsTabProps {
  pageId: string | null;
}

interface AnalysisStats {
  totalElements: number;
  questionCount: number;
  processingTime: number | null;
  classDistribution: Record<string, number>;
  confidenceScores: Record<string, number>;
}

const formatProcessingTime = (value: number | null): string => {
  if (value == null) {
    return "-";
  }
  if (value < 1) {
    return `${(value * 1000).toFixed(0)}ms`;
  }
  return `${value.toFixed(2)}초`;
};

const mapNumericRecord = (
  record: PageStatsResponse["class_distribution"] | undefined
): Record<string, number> => {
  if (!record) {
    return {};
  }
  return Object.entries(record).reduce<Record<string, number>>((acc, [key, value]) => {
    if (typeof value === "number" && !Number.isNaN(value)) {
      acc[key] = value;
    }
    return acc;
  }, {});
};

const AIStatsTab: React.FC<AIStatsTabProps> = ({ pageId }) => {
  const [stats, setStats] = useState<AnalysisStats | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!pageId) {
      setStats(null);
      setError("선택된 페이지가 없습니다.");
      return;
    }

    const numericPageId = Number(pageId);
    if (!Number.isFinite(numericPageId)) {
      setStats(null);
      setError("잘못된 페이지 ID입니다.");
      return;
    }

    let isSubscribed = true;
    setIsLoading(true);
    setError(null);

    analysisService
      .getPageStats(numericPageId)
      .then((data) => {
        if (!isSubscribed) {
          return;
        }

        setStats({
          totalElements: data.total_elements ?? 0,
          questionCount: data.anchor_element_count ?? 0,
          processingTime:
            typeof data.processing_time === "number" ? data.processing_time : null,
          classDistribution: mapNumericRecord(data.class_distribution),
          confidenceScores: mapNumericRecord(data.confidence_scores),
        });
      })
      .catch((fetchError) => {
        if (!isSubscribed) {
          return;
        }
        console.error("페이지 통계 조회 실패", fetchError);
        setStats(null);
        setError("통계를 불러오는 중 오류가 발생했습니다.");
      })
      .finally(() => {
        if (isSubscribed) {
          setIsLoading(false);
        }
      });

    return () => {
      isSubscribed = false;
    };
  }, [pageId]);

  const statCards = useMemo(
    () => [
      {
        icon: "📊",
        label: "총 요소 개수",
        value: stats?.totalElements ?? "-",
        color: "#2196F3",
      },
      {
        icon: "❓",
        label: "문제 개수",
        value: stats?.questionCount ?? "-",
        color: "#4CAF50",
      },
      {
        icon: "⏱️",
        label: "처리 시간",
        value: formatProcessingTime(stats?.processingTime ?? null),
        color: "#FF9800",
      },
    ],
    [stats]
  );

  const distributionData = useMemo(() => {
    if (!stats) {
      return [];
    }
    const entries = Object.entries(stats.classDistribution);
    if (entries.length === 0) {
      return [];
    }
    const maxCount = Math.max(...entries.map(([, count]) => count as number));
    if (maxCount === 0) {
      return entries.map(([className]) => ({
        className,
        count: 0,
        percentage: 0,
      }));
    }

    return entries.map(([className, count]) => ({
      className,
      count,
      percentage: ((count as number) / maxCount) * 100,
    }));
  }, [stats]);

  if (!pageId) {
    return (
      <div className={styles.aiStatsTab}>
        <div className={styles.emptyState}>
          <p>페이지를 선택하면 통계를 확인할 수 있습니다.</p>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className={styles.aiStatsTab}>
        <div className={styles.loadingState}>통계를 불러오는 중입니다...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.aiStatsTab}>
        <div className={styles.errorState}>{error}</div>
      </div>
    );
  }

  if (!stats) {
    return (
      <div className={styles.aiStatsTab}>
        <div className={styles.emptyState}>
          <p>표시할 통계가 없습니다.</p>
        </div>
      </div>
    );
  }

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
            {Object.entries(stats.classDistribution).map(([className, count]) => {
              const confidence = stats.confidenceScores[className];
              const confidenceLabel =
                typeof confidence === "number"
                  ? `${(confidence * 100).toFixed(1)}%`
                  : "-";
              return (
                <tr key={className}>
                  <td>{className}</td>
                  <td>{count}</td>
                  <td>{confidenceLabel}</td>
                </tr>
              );
            })}
            {Object.keys(stats.classDistribution).length === 0 && (
              <tr>
                <td colSpan={3}>클래스 분포 정보가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AIStatsTab;

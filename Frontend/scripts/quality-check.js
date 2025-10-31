#!/usr/bin/env node

/**
 * SmartEye 프론트엔드 품질 검증 스크립트
 * 코드 품질, 성능, 안정성을 종합적으로 검사합니다.
 */

const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const { promisify } = require('util');

const execAsync = promisify(exec);

// 색상 코드
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m',
  bold: '\x1b[1m'
};

// 로그 헬퍼
const log = {
  info: (msg) => console.log(`${colors.blue}ℹ ${msg}${colors.reset}`),
  success: (msg) => console.log(`${colors.green}✅ ${msg}${colors.reset}`),
  warning: (msg) => console.log(`${colors.yellow}⚠️ ${msg}${colors.reset}`),
  error: (msg) => console.log(`${colors.red}❌ ${msg}${colors.reset}`),
  header: (msg) => console.log(`\n${colors.bold}${colors.cyan}🔍 ${msg}${colors.reset}`)
};

// 품질 기준
const QUALITY_STANDARDS = {
  testCoverage: 80, // 최소 테스트 커버리지 80%
  maxFileSize: 500, // 최대 파일 크기 500줄
  maxComplexity: 10, // 최대 순환 복잡도 10
  maxDuplication: 5, // 최대 중복 코드 5%
  performance: {
    normalizeTime: 100, // 정규화 최대 100ms
    renderTime: 50, // 렌더링 최대 50ms
    memoryLeak: 10 // 메모리 누수 최대 10MB
  }
};

// 프로젝트 루트 디렉토리
const PROJECT_ROOT = path.resolve(__dirname, '..');
const SRC_DIR = path.join(PROJECT_ROOT, 'src');

/**
 * 파일 및 디렉토리 분석
 */
async function analyzeProject() {
  log.header('프로젝트 구조 분석');

  const stats = {
    totalFiles: 0,
    totalLines: 0,
    componentFiles: 0,
    utilFiles: 0,
    testFiles: 0,
    largeFiles: []
  };

  function analyzeDirectory(dirPath) {
    const items = fs.readdirSync(dirPath);

    for (const item of items) {
      const itemPath = path.join(dirPath, item);
      const stat = fs.statSync(itemPath);

      if (stat.isDirectory() && !item.startsWith('.') && item !== 'node_modules') {
        analyzeDirectory(itemPath);
      } else if (stat.isFile() && (item.endsWith('.js') || item.endsWith('.jsx'))) {
        stats.totalFiles++;

        const content = fs.readFileSync(itemPath, 'utf8');
        const lineCount = content.split('\n').length;
        stats.totalLines += lineCount;

        // 파일 유형 분류
        if (item.includes('.test.') || item.includes('.spec.')) {
          stats.testFiles++;
        } else if (itemPath.includes('components')) {
          stats.componentFiles++;
        } else if (itemPath.includes('utils')) {
          stats.utilFiles++;
        }

        // 큰 파일 감지
        if (lineCount > QUALITY_STANDARDS.maxFileSize) {
          stats.largeFiles.push({
            file: path.relative(PROJECT_ROOT, itemPath),
            lines: lineCount
          });
        }
      }
    }
  }

  analyzeDirectory(SRC_DIR);

  log.info(`총 파일 수: ${stats.totalFiles}`);
  log.info(`총 라인 수: ${stats.totalLines.toLocaleString()}`);
  log.info(`컴포넌트 파일: ${stats.componentFiles}`);
  log.info(`유틸리티 파일: ${stats.utilFiles}`);
  log.info(`테스트 파일: ${stats.testFiles}`);

  if (stats.largeFiles.length > 0) {
    log.warning(`큰 파일 감지 (${QUALITY_STANDARDS.maxFileSize}줄 초과):`);
    stats.largeFiles.forEach(({ file, lines }) => {
      log.warning(`  ${file}: ${lines}줄`);
    });
  } else {
    log.success('모든 파일이 적절한 크기입니다');
  }

  return stats;
}

/**
 * 테스트 실행 및 커버리지 측정
 */
async function runTests() {
  log.header('테스트 실행 및 커버리지 측정');

  try {
    // 테스트 실행
    log.info('테스트 실행 중...');
    const { stdout: testOutput } = await execAsync('npm test -- --coverage --watchAll=false', {
      cwd: PROJECT_ROOT
    });

    // 커버리지 결과 파싱 (간단한 예시)
    const coverageMatch = testOutput.match(/All files\s+\|\s+(\d+\.?\d*)/);
    const coverage = coverageMatch ? parseFloat(coverageMatch[1]) : 0;

    if (coverage >= QUALITY_STANDARDS.testCoverage) {
      log.success(`테스트 커버리지: ${coverage}%`);
    } else {
      log.warning(`테스트 커버리지 부족: ${coverage}% (최소 ${QUALITY_STANDARDS.testCoverage}% 필요)`);
    }

    return { success: true, coverage };
  } catch (error) {
    log.error('테스트 실행 실패');
    console.error(error.stdout || error.message);
    return { success: false, coverage: 0 };
  }
}

/**
 * 코드 품질 분석
 */
async function analyzeCodeQuality() {
  log.header('코드 품질 분석');

  const issues = [];

  // 핵심 유틸리티 함수들 검증
  const utilFiles = [
    'src/utils/dataUtils.js',
    'src/utils/errorHandler.js'
  ];

  for (const filePath of utilFiles) {
    const fullPath = path.join(PROJECT_ROOT, filePath);
    if (!fs.existsSync(fullPath)) {
      issues.push(`필수 파일 누락: ${filePath}`);
      continue;
    }

    const content = fs.readFileSync(fullPath, 'utf8');

    // 함수 복잡도 간단 체크 (실제로는 ESLint 플러그인 사용 권장)
    const functionMatches = content.match(/function\s+\w+|=>\s*{|const\s+\w+\s*=/g);
    const functionCount = functionMatches ? functionMatches.length : 0;

    if (functionCount > 20) {
      issues.push(`${filePath}: 함수가 너무 많습니다 (${functionCount}개)`);
    }

    // TODO 및 FIXME 체크
    const todoMatches = content.match(/\/\/\s*(TODO|FIXME|XXX)/gi);
    if (todoMatches && todoMatches.length > 5) {
      issues.push(`${filePath}: 미완성 작업이 많습니다 (${todoMatches.length}개)`);
    }

    // 에러 처리 체크
    const tryBlocks = content.match(/try\s*{/g);
    const catchBlocks = content.match(/catch\s*\(/g);
    if (tryBlocks && catchBlocks && tryBlocks.length !== catchBlocks.length) {
      issues.push(`${filePath}: try-catch 블록 불일치`);
    }

    log.info(`${filePath}: 함수 ${functionCount}개, TODO ${todoMatches?.length || 0}개`);
  }

  if (issues.length === 0) {
    log.success('코드 품질 검사 통과');
  } else {
    log.warning('코드 품질 이슈 발견:');
    issues.forEach(issue => log.warning(`  ${issue}`));
  }

  return { issues };
}

/**
 * 성능 테스트
 */
async function performanceTest() {
  log.header('성능 테스트');

  try {
    // 성능 테스트 실행
    log.info('성능 테스트 실행 중...');
    const { stdout } = await execAsync('npm test -- --testNamePattern="성능 테스트" --verbose', {
      cwd: PROJECT_ROOT
    });

    // 성능 결과 파싱 (실제로는 더 정교한 파싱 필요)
    const performanceResults = {
      normalizeTime: extractPerformanceMetric(stdout, '정규화.*?(\\d+\\.?\\d*)ms'),
      renderTime: extractPerformanceMetric(stdout, '렌더링.*?(\\d+\\.?\\d*)ms'),
      memoryUsage: extractPerformanceMetric(stdout, '메모리.*?(\\d+\\.?\\d*)MB')
    };

    // 성능 기준 검증
    const performance = QUALITY_STANDARDS.performance;
    let passed = 0;
    let total = 0;

    if (performanceResults.normalizeTime) {
      total++;
      if (performanceResults.normalizeTime <= performance.normalizeTime) {
        log.success(`정규화 성능: ${performanceResults.normalizeTime}ms`);
        passed++;
      } else {
        log.warning(`정규화 성능 초과: ${performanceResults.normalizeTime}ms (기준: ${performance.normalizeTime}ms)`);
      }
    }

    if (performanceResults.renderTime) {
      total++;
      if (performanceResults.renderTime <= performance.renderTime) {
        log.success(`렌더링 성능: ${performanceResults.renderTime}ms`);
        passed++;
      } else {
        log.warning(`렌더링 성능 초과: ${performanceResults.renderTime}ms (기준: ${performance.renderTime}ms)`);
      }
    }

    if (performanceResults.memoryUsage) {
      total++;
      if (performanceResults.memoryUsage <= performance.memoryLeak) {
        log.success(`메모리 사용량: ${performanceResults.memoryUsage}MB`);
        passed++;
      } else {
        log.warning(`메모리 사용량 초과: ${performanceResults.memoryUsage}MB (기준: ${performance.memoryLeak}MB)`);
      }
    }

    return { passed, total, results: performanceResults };
  } catch (error) {
    log.error('성능 테스트 실행 실패');
    return { passed: 0, total: 0, results: {} };
  }
}

/**
 * 안정성 테스트
 */
async function stabilityTest() {
  log.header('안정성 테스트');

  try {
    log.info('안정성 테스트 실행 중...');
    const { stdout } = await execAsync('npm test -- --testNamePattern="안정성 테스트" --verbose', {
      cwd: PROJECT_ROOT
    });

    // 테스트 결과 파싱
    const passedTests = (stdout.match(/✓/g) || []).length;
    const failedTests = (stdout.match(/✗/g) || []).length;
    const totalTests = passedTests + failedTests;

    if (totalTests > 0) {
      const successRate = (passedTests / totalTests) * 100;

      if (successRate >= 95) {
        log.success(`안정성 테스트: ${passedTests}/${totalTests} 통과 (${successRate.toFixed(1)}%)`);
      } else {
        log.warning(`안정성 테스트: ${passedTests}/${totalTests} 통과 (${successRate.toFixed(1)}%)`);
      }

      return { passed: passedTests, total: totalTests, successRate };
    } else {
      log.warning('안정성 테스트가 실행되지 않았습니다');
      return { passed: 0, total: 0, successRate: 0 };
    }
  } catch (error) {
    log.error('안정성 테스트 실행 실패');
    return { passed: 0, total: 0, successRate: 0 };
  }
}

/**
 * 보안 검사
 */
async function securityCheck() {
  log.header('보안 검사');

  const securityIssues = [];

  // 핵심 파일들에서 보안 이슈 체크
  const filesToCheck = [
    'src/services/apiService.js',
    'src/utils/errorHandler.js'
  ];

  for (const filePath of filesToCheck) {
    const fullPath = path.join(PROJECT_ROOT, filePath);
    if (!fs.existsSync(fullPath)) continue;

    const content = fs.readFileSync(fullPath, 'utf8');

    // 위험한 패턴 체크
    const dangerousPatterns = [
      { pattern: /eval\s*\(/g, issue: 'eval() 사용' },
      { pattern: /innerHTML\s*=/g, issue: 'innerHTML 직접 할당' },
      { pattern: /document\.write/g, issue: 'document.write 사용' },
      { pattern: /window\.location\s*=/g, issue: '직접 리다이렉트' }
    ];

    for (const { pattern, issue } of dangerousPatterns) {
      const matches = content.match(pattern);
      if (matches) {
        securityIssues.push(`${filePath}: ${issue} (${matches.length}회)`);
      }
    }

    // dangerouslySetInnerHTML 체크 (React)
    const dangerousHTML = content.match(/dangerouslySetInnerHTML/g);
    if (dangerousHTML) {
      log.warning(`${filePath}: dangerouslySetInnerHTML 사용 확인 필요`);
    }
  }

  if (securityIssues.length === 0) {
    log.success('보안 검사 통과');
  } else {
    log.warning('보안 이슈 발견:');
    securityIssues.forEach(issue => log.warning(`  ${issue}`));
  }

  return { issues: securityIssues };
}

/**
 * 접근성 검사
 */
async function accessibilityCheck() {
  log.header('접근성 검사');

  const a11yIssues = [];
  const componentDir = path.join(SRC_DIR, 'components');

  if (fs.existsSync(componentDir)) {
    const componentFiles = fs.readdirSync(componentDir)
      .filter(file => file.endsWith('.jsx'))
      .map(file => path.join(componentDir, file));

    for (const filePath of componentFiles) {
      const content = fs.readFileSync(filePath, 'utf8');

      // 접근성 이슈 체크
      const checks = [
        {
          pattern: /<img(?![^>]*alt=)/g,
          issue: 'alt 속성이 없는 이미지'
        },
        {
          pattern: /<button(?![^>]*aria-label)(?![^>]*>.*<\/button>)/g,
          issue: '라벨이 없는 버튼'
        },
        {
          pattern: /<input(?![^>]*aria-label)(?![^>]*id=)/g,
          issue: '라벨이 없는 입력 필드'
        }
      ];

      for (const { pattern, issue } of checks) {
        const matches = content.match(pattern);
        if (matches) {
          a11yIssues.push(`${path.basename(filePath)}: ${issue} (${matches.length}개)`);
        }
      }
    }
  }

  if (a11yIssues.length === 0) {
    log.success('접근성 검사 통과');
  } else {
    log.warning('접근성 이슈 발견:');
    a11yIssues.forEach(issue => log.warning(`  ${issue}`));
  }

  return { issues: a11yIssues };
}

/**
 * 성능 메트릭 추출 헬퍼
 */
function extractPerformanceMetric(output, pattern) {
  const match = output.match(new RegExp(pattern));
  return match ? parseFloat(match[1]) : null;
}

/**
 * 종합 품질 점수 계산
 */
function calculateQualityScore(results) {
  const weights = {
    test: 0.25,
    code: 0.20,
    performance: 0.20,
    stability: 0.15,
    security: 0.10,
    accessibility: 0.10
  };

  let totalScore = 0;

  // 테스트 점수 (커버리지 기반)
  const testScore = Math.min(results.test.coverage / QUALITY_STANDARDS.testCoverage, 1) * 100;
  totalScore += testScore * weights.test;

  // 코드 품질 점수 (이슈 수 기반)
  const codeScore = Math.max(0, 100 - (results.code.issues.length * 10));
  totalScore += codeScore * weights.code;

  // 성능 점수
  const perfScore = results.performance.total > 0 ?
    (results.performance.passed / results.performance.total) * 100 : 50;
  totalScore += perfScore * weights.performance;

  // 안정성 점수
  const stabilityScore = results.stability.successRate || 0;
  totalScore += stabilityScore * weights.stability;

  // 보안 점수
  const securityScore = Math.max(0, 100 - (results.security.issues.length * 20));
  totalScore += securityScore * weights.security;

  // 접근성 점수
  const a11yScore = Math.max(0, 100 - (results.accessibility.issues.length * 15));
  totalScore += a11yScore * weights.accessibility;

  return Math.round(totalScore);
}

/**
 * 메인 실행 함수
 */
async function main() {
  console.log(`${colors.bold}${colors.cyan}`);
  console.log('╔══════════════════════════════════════════════════════════════╗');
  console.log('║                    SmartEye 품질 검증                         ║');
  console.log('╚══════════════════════════════════════════════════════════════╝');
  console.log(`${colors.reset}\n`);

  const startTime = Date.now();
  const results = {};

  try {
    // 1. 프로젝트 분석
    results.project = await analyzeProject();

    // 2. 테스트 실행
    results.test = await runTests();

    // 3. 코드 품질 분석
    results.code = await analyzeCodeQuality();

    // 4. 성능 테스트
    results.performance = await performanceTest();

    // 5. 안정성 테스트
    results.stability = await stabilityTest();

    // 6. 보안 검사
    results.security = await securityCheck();

    // 7. 접근성 검사
    results.accessibility = await accessibilityCheck();

    // 8. 종합 점수 계산
    const qualityScore = calculateQualityScore(results);

    // 결과 요약
    log.header('검증 결과 요약');
    console.log(`${colors.bold}품질 점수: ${qualityScore}/100${colors.reset}`);

    if (qualityScore >= 90) {
      log.success('우수한 품질입니다! 🏆');
    } else if (qualityScore >= 80) {
      log.success('양호한 품질입니다! 👍');
    } else if (qualityScore >= 70) {
      log.warning('개선이 필요합니다. 📈');
    } else {
      log.error('심각한 품질 문제가 있습니다. 🚨');
    }

    const endTime = Date.now();
    log.info(`검증 완료 시간: ${((endTime - startTime) / 1000).toFixed(1)}초`);

  } catch (error) {
    log.error('품질 검증 중 오류 발생:');
    console.error(error);
    process.exit(1);
  }
}

// 스크립트 실행
if (require.main === module) {
  main();
}
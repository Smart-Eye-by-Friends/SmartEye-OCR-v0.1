#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

class CopilotInstructionsUpdater {
    constructor() {
        this.projectRoot = process.cwd();
        this.instructionsPath = path.join(this.projectRoot, '.github/copilot-instructions.md');
    }

    // 현재 코드베이스 분석
    analyzeCodebase() {
        console.log('🔍 Analyzing codebase structure...');
        
        const analysis = {
            controllers: this.scanControllers(),
            services: this.scanServices(),
            entities: this.scanEntities(),
            config: this.scanConfiguration(),
            apiEndpoints: [],
            lamService: this.analyzeLAMService(),
            dockerConfig: this.analyzeDockerConfig(),
            buildInfo: this.analyzeBuildInfo(),
            lastUpdated: new Date().toISOString()
        };
        
        // API 엔드포인트 집계
        analysis.apiEndpoints = this.aggregateEndpoints(analysis);
        
        return analysis;
    }

    scanControllers() {
        const controllerDir = path.join(this.projectRoot, 'src/main/java/com/smarteye/controller');
        const controllers = [];
        
        if (fs.existsSync(controllerDir)) {
            const files = fs.readdirSync(controllerDir).filter(f => f.endsWith('.java'));
            console.log(`📁 Found ${files.length} controllers`);
            
            files.forEach(file => {
                const content = fs.readFileSync(path.join(controllerDir, file), 'utf8');
                const endpoints = this.extractEndpoints(content);
                controllers.push({
                    name: file.replace('.java', ''),
                    endpoints: endpoints,
                    isMainEntry: content.includes('@PostMapping("/complete")'),
                    hasAsync: content.includes('CompletableFuture'),
                    requestMapping: this.extractRequestMapping(content)
                });
            });
        }
        
        return controllers;
    }

    extractEndpoints(content) {
        const endpoints = [];
        const mappingRegex = /@(Get|Post|Put|Delete)Mapping\s*\(\s*"([^"]+)"\s*\)/g;
        let match;
        
        while ((match = mappingRegex.exec(content)) !== null) {
            endpoints.push({
                method: match[1].toUpperCase(),
                path: match[2]
            });
        }
        
        return endpoints;
    }

    extractRequestMapping(content) {
        const mappingMatch = content.match(/@RequestMapping\s*\(\s*"([^"]+)"\s*\)/);
        return mappingMatch ? mappingMatch[1] : '';
    }

    scanServices() {
        const serviceDir = path.join(this.projectRoot, 'src/main/java/com/smarteye/service');
        const services = [];
        
        if (fs.existsSync(serviceDir)) {
            const files = fs.readdirSync(serviceDir).filter(f => f.endsWith('.java'));
            console.log(`🔧 Found ${files.length} services`);
            
            files.forEach(file => {
                const content = fs.readFileSync(path.join(serviceDir, file), 'utf8');
                services.push({
                    name: file.replace('.java', ''),
                    isCore: ['AnalysisService', 'LAMService', 'TSPMService', 'CIMService'].includes(file.replace('.java', '')),
                    hasAsync: content.includes('@Async'),
                    hasCaching: content.includes('@Cacheable'),
                    hasTransactional: content.includes('@Transactional'),
                    communicatesWithLAM: content.includes('lamClient') || content.includes('LAM_SERVICE_URL')
                });
            });
        }
        
        return services;
    }

    scanEntities() {
        const entityDir = path.join(this.projectRoot, 'src/main/java/com/smarteye/model/entity');
        const entities = [];
        
        if (fs.existsSync(entityDir)) {
            const files = fs.readdirSync(entityDir).filter(f => f.endsWith('.java'));
            console.log(`📊 Found ${files.length} entities`);
            
            files.forEach(file => {
                entities.push(file.replace('.java', ''));
            });
        }
        
        return entities;
    }

    scanConfiguration() {
        const configPath = path.join(this.projectRoot, 'src/main/resources/application.yml');
        let config = { exists: false };
        
        if (fs.existsSync(configPath)) {
            const content = fs.readFileSync(configPath, 'utf8');
            config = {
                exists: true,
                hasLAMConfig: content.includes('smarteye.lam'),
                hasOpenAI: content.includes('openai'),
                hasTesseract: content.includes('tesseract'),
                hasRedis: content.includes('redis'),
                profiles: this.extractProfiles(content),
                lamServiceUrl: this.extractLAMServiceUrl(content)
            };
        }
        
        return config;
    }

    extractProfiles(content) {
        const profiles = [];
        const lines = content.split('\n');
        
        lines.forEach(line => {
            if (line.includes('spring.profiles.active')) {
                const match = line.match(/:\s*(.+)/);
                if (match) profiles.push(match[1].trim());
            }
        });
        
        return profiles;
    }

    extractLAMServiceUrl(content) {
        const match = content.match(/url:\s*\$\{LAM_SERVICE_URL:([^}]+)\}/);
        return match ? match[1] : 'http://localhost:8081';
    }

    analyzeLAMService() {
        const lamDir = path.join(this.projectRoot, 'smarteye-lam-service');
        let lamInfo = { exists: false };
        
        if (fs.existsSync(lamDir)) {
            console.log('🐍 Analyzing LAM microservice...');
            const mainPy = path.join(lamDir, 'app/main.py');
            const requirementsTxt = path.join(lamDir, 'requirements.txt');
            
            if (fs.existsSync(mainPy)) {
                const content = fs.readFileSync(mainPy, 'utf8');
                lamInfo = {
                    exists: true,
                    hasRedis: content.includes('redis'),
                    hasAsync: content.includes('async def'),
                    hasCORS: content.includes('CORSMiddleware'),
                    endpoints: this.extractPythonEndpoints(content),
                    hasRequirements: fs.existsSync(requirementsTxt)
                };
            }
        }
        
        return lamInfo;
    }

    extractPythonEndpoints(content) {
        const endpoints = [];
        const endpointRegex = /@app\.(get|post|put|delete)\s*\(\s*"([^"]+)"\s*\)/g;
        let match;
        
        while ((match = endpointRegex.exec(content)) !== null) {
            endpoints.push({
                method: match[1].toUpperCase(),
                path: match[2]
            });
        }
        
        return endpoints;
    }

    analyzeDockerConfig() {
        const dockerCompose = path.join(this.projectRoot, 'docker-compose.yml');
        const dockerComposeDev = path.join(this.projectRoot, 'docker-compose.dev.yml');
        
        return {
            hasDockerCompose: fs.existsSync(dockerCompose),
            hasDevCompose: fs.existsSync(dockerComposeDev),
            hasDockerfile: fs.existsSync(path.join(this.projectRoot, 'Dockerfile'))
        };
    }

    analyzeBuildInfo() {
        const buildGradle = path.join(this.projectRoot, 'build.gradle');
        let buildInfo = { exists: false };
        
        if (fs.existsSync(buildGradle)) {
            const content = fs.readFileSync(buildGradle, 'utf8');
            const versionMatch = content.match(/version\s*=\s*'([^']+)'/);
            const groupMatch = content.match(/group\s*=\s*'([^']+)'/);
            const javaMatch = content.match(/sourceCompatibility\s*=\s*'([^']+)'/);
            
            buildInfo = {
                exists: true,
                version: versionMatch ? versionMatch[1] : 'unknown',
                group: groupMatch ? groupMatch[1] : 'unknown',
                javaVersion: javaMatch ? javaMatch[1] : 'unknown'
            };
        }
        
        return buildInfo;
    }

    aggregateEndpoints(analysis) {
        let allEndpoints = [];
        
        // Java 컨트롤러 엔드포인트
        analysis.controllers.forEach(controller => {
            const baseMapping = controller.requestMapping || '';
            controller.endpoints.forEach(endpoint => {
                allEndpoints.push({
                    method: endpoint.method,
                    path: baseMapping + endpoint.path,
                    service: `${controller.name} (Java)`,
                    isMainEntry: controller.isMainEntry && endpoint.path === '/complete'
                });
            });
        });
        
        // LAM 서비스 엔드포인트
        if (analysis.lamService.exists) {
            analysis.lamService.endpoints.forEach(endpoint => {
                allEndpoints.push({
                    method: endpoint.method,
                    path: endpoint.path,
                    service: 'LAM Service (Python)',
                    isMainEntry: false
                });
            });
        }
        
        return allEndpoints;
    }

    // 업데이트된 지침 생성
    generateInstructions(analysis) {
        const timestamp = new Date(analysis.lastUpdated).toLocaleString('ko-KR');
        
        return `# SmartEye AI Coding Agent Instructions
<!-- Auto-generated on ${analysis.lastUpdated} -->

## Architecture Overview
SmartEye is a **hybrid document analysis system** with a 3-module pipeline:
- **LAM** (Layout Analysis): Python FastAPI microservice (port 8081) using DocLayout-YOLO
- **TSPM** (Text & Semantic Processing): Java native service using Tesseract OCR + OpenAI Vision API  
- **CIM** (Content Integration): Java service that merges LAM+TSPM results

Key insight: This is NOT a monolithic Spring Boot app - it's a microservice architecture where the main Spring Boot backend (port 8080) orchestrates Python services.

## Current System Status
**Project Version**: ${analysis.buildInfo.version} (Java ${analysis.buildInfo.javaVersion})
**Controllers**: ${analysis.controllers.length} found (${analysis.controllers.filter(c => c.isMainEntry).length} main entry)
**Core Services**: ${analysis.services.filter(s => s.isCore).length}/4 implemented
**LAM Service**: ${analysis.lamService.exists ? '✅ Active' : '❌ Not found'}
**Database Entities**: ${analysis.entities.length} entities
**Docker Ready**: ${analysis.dockerConfig.hasDockerCompose ? '✅' : '❌'}

## Critical Development Patterns

### Service Integration Pattern
The main processing flow follows this strict sequence:
\`\`\`java
// In AnalysisController.java - THIS IS THE MAIN ENTRY POINT
@PostMapping("/complete") // Full pipeline
LAMService.analyzeLayout() → TSPMService.performTSPMAnalysis() → CIMService.integrateResults()
\`\`\`

Each service operates on \`AnalysisJob\` entities with \`jobId\` tracking. Always check job status before proceeding to next step.

### Database Entity Pattern
All analysis results use JPA entities in \`src/main/java/com/smarteye/model/entity/\`:
${analysis.entities.map(entity => `- \`${entity}\``).join('\n')}

### Configuration Pattern
Environment-specific configs in \`application.yml\`:
\`\`\`yaml
smarteye:
  lam.service.url: ${analysis.config.lamServiceUrl}  # Python service
  tspm.use-java-native: true  # Toggle between Java/Python TSPM
  openai.api-key: \${OPENAI_API_KEY}  # Required for Vision API
\`\`\`

## Essential Development Commands

### Build & Run (Use the scripts!)
\`\`\`bash
# Primary development workflow
./scripts/run.sh dev           # Start in dev mode  
./scripts/run.sh build         # Clean build
docker-compose -f docker-compose.dev.yml up -d  # Full system with LAM service

# NOT just ./gradlew bootRun - that won't start LAM microservice
\`\`\`

### Testing Critical Services
\`\`\`bash
# Test LAM microservice connectivity
curl http://localhost:8081/health

# Test TSPM Java native
curl http://localhost:8080/api/test/tspm-java

# Test full pipeline
curl -X POST -F "file=@test.jpg" http://localhost:8080/api/analysis/complete
\`\`\`

## Current API Endpoints
${this.generateEndpointsList(analysis.apiEndpoints)}

## LAM Microservice Integration
**Critical**: LAM runs separately as Python FastAPI service. The Java backend communicates via HTTP:

${analysis.lamService.exists ? `
✅ **LAM Service Status**: 
- Endpoints: ${analysis.lamService.endpoints.length} available
- Redis Caching: ${analysis.lamService.hasRedis ? 'Enabled' : 'Disabled'}
- Async Processing: ${analysis.lamService.hasAsync ? 'Enabled' : 'Disabled'}
- CORS: ${analysis.lamService.hasCORS ? 'Configured' : 'Not configured'}
` : `
❌ **LAM Service**: Not found in \`smarteye-lam-service/\` directory
`}

\`\`\`java
// In LAMService.java - this calls external Python service
@Value("\${smarteye.lam.service.url}")
private String lamServiceUrl;  // ${analysis.config.lamServiceUrl}

LAMAnalysisResponse response = lamClient.analyzeLayout(request);
\`\`\`

When developing LAM features:
1. Modify Python code in \`smarteye-lam-service/app/\`
2. Test with \`uvicorn app.main:app --reload\` in that directory
3. Update Java DTOs in \`src/main/java/com/smarteye/dto/lam/\` to match Python responses

## Key Integration Points

### Job Status Tracking
Every operation must update \`AnalysisJob.status\` and \`progress\`:
\`\`\`java
job.setStatus("PROCESSING");  // CREATED → PROCESSING → COMPLETED/FAILED
job.setProgress(50);          // 0-100 for frontend progress bars
analysisJobRepository.save(job);
\`\`\`

### Error Handling Pattern
Always wrap microservice calls:
\`\`\`java
try {
    LAMAnalysisResponse response = lamClient.analyzeLayout(request);
} catch (Exception e) {
    job.setStatus("FAILED");
    job.setErrorMessage("LAM 마이크로서비스 분석 실패: " + e.getMessage());
    analysisJobRepository.save(job);
}
\`\`\`

### File Processing Flow
1. Upload to \`./temp\` directory with \`jobId\` prefix
2. Convert to base64 for Python service communication
3. Store results in JPA entities
4. Clean up temp files after processing

## Performance Considerations
${this.generatePerformanceStatus(analysis)}

## Testing Strategy
- Unit tests: Focus on service layer logic, mock external calls
- Integration tests: Use \`TSPMTestController\` endpoints for debugging
- End-to-end: Docker Compose environment required for LAM service
- Performance: Check \`/api/monitoring/health\` for system status

Always test LAM connectivity first when debugging - most issues stem from Python service communication failures.

---
*🤖 Auto-updated based on codebase analysis*  
*Last scan: ${timestamp}*  
*Update script: \`./scripts/update-copilot-instructions.js\`*`;
    }

    generateEndpointsList(endpoints) {
        const mainEntry = endpoints.find(e => e.isMainEntry);
        let result = '';
        
        if (mainEntry) {
            result += `\n**🎯 Main Entry Point:**\n- **${mainEntry.method}** \`${mainEntry.path}\` - ${mainEntry.service}\n`;
        }
        
        const otherEndpoints = endpoints.filter(e => !e.isMainEntry).slice(0, 8);
        if (otherEndpoints.length > 0) {
            result += `\n**Other Key Endpoints:**\n`;
            otherEndpoints.forEach(endpoint => {
                result += `- **${endpoint.method}** \`${endpoint.path}\` (${endpoint.service})\n`;
            });
        }
        
        if (endpoints.length > 9) {
            result += `\n*... and ${endpoints.length - 9} more endpoints*`;
        }
        
        return result;
    }

    generatePerformanceStatus(analysis) {
        const status = [];
        
        if (analysis.services.some(s => s.hasAsync)) {
            status.push('- ✅ Async processing enabled');
        } else {
            status.push('- ⚠️ No async processing detected');
        }
        
        if (analysis.services.some(s => s.hasCaching)) {
            status.push('- ✅ Caching implemented');
        } else {
            status.push('- ⚠️ No caching detected');
        }
        
        if (analysis.lamService.hasRedis) {
            status.push('- ✅ Redis caching in LAM service');
        } else {
            status.push('- ⚠️ No Redis caching in LAM');
        }
        
        if (analysis.config.hasRedis) {
            status.push('- ✅ Redis configured in main app');
        }
        
        return status.join('\n');
    }

    // 메인 업데이트 함수
    updateInstructions() {
        console.log('🤖 SmartEye Copilot Instructions Auto-Updater');
        console.log('============================================');
        
        try {
            const analysis = this.analyzeCodebase();
            
            console.log('📝 Generating updated instructions...');
            const newInstructions = this.generateInstructions(analysis);
            
            // 기존 파일과 비교
            let hasChanges = true;
            if (fs.existsSync(this.instructionsPath)) {
                const currentContent = fs.readFileSync(this.instructionsPath, 'utf8');
                // 타임스탬프 부분을 제외하고 비교
                const currentWithoutTimestamp = currentContent.replace(/Last scan: [^\n]+/, '');
                const newWithoutTimestamp = newInstructions.replace(/Last scan: [^\n]+/, '');
                
                if (currentWithoutTimestamp === newWithoutTimestamp) {
                    console.log('✅ Instructions are up to date (no structural changes)');
                    hasChanges = false;
                }
            }
            
            // 항상 타임스탬프는 업데이트
            fs.writeFileSync(this.instructionsPath, newInstructions);
            
            if (hasChanges) {
                console.log('🎉 Copilot instructions updated with new changes!');
            } else {
                console.log('🕒 Timestamp updated, no structural changes detected');
            }
            
            // 분석 결과 요약
            console.log('\n📊 Analysis Summary:');
            console.log(`   Controllers: ${analysis.controllers.length}`);
            console.log(`   Services: ${analysis.services.length} (${analysis.services.filter(s => s.isCore).length} core)`);
            console.log(`   Entities: ${analysis.entities.length}`);
            console.log(`   API Endpoints: ${analysis.apiEndpoints.length}`);
            console.log(`   LAM Service: ${analysis.lamService.exists ? 'Active' : 'Not found'}`);
            
            return hasChanges;
            
        } catch (error) {
            console.error('❌ Error updating instructions:', error.message);
            console.error(error.stack);
            process.exit(1);
        }
    }
}

// 실행
if (require.main === module) {
    const updater = new CopilotInstructionsUpdater();
    const hasChanges = updater.updateInstructions();
    process.exit(hasChanges ? 0 : 0);  // 항상 성공으로 처리
}

module.exports = CopilotInstructionsUpdater;

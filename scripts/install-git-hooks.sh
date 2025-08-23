#!/bin/bash

# Git Hooks 설치 스크립트
echo "🪝 Installing SmartEye Git Hooks..."

# 프로젝트 루트로 이동
cd "$(dirname "$0")/.."

# .git 디렉터리 확인
if [ ! -d ".git" ]; then
    echo "❌ Not a git repository. Please run this from the project root."
    exit 1
fi

# hooks 디렉터리 생성
mkdir -p .git/hooks

# Pre-commit hook 복사
echo "📋 Installing pre-commit hook..."
cat > .git/hooks/pre-commit << 'EOF'
#!/bin/bash

# SmartEye Pre-commit Hook - Copilot Instructions Auto-updater
# This hook automatically updates .github/copilot-instructions.md when relevant files are changed

echo "🪝 Pre-commit hook: Checking for copilot instructions updates..."

# 변경된 파일 목록 가져오기
CHANGED_FILES=$(git diff --cached --name-only)
UPDATE_NEEDED=false

# 아키텍처 관련 파일 변경사항 체크
for file in $CHANGED_FILES; do
    case $file in
        src/main/java/com/smarteye/controller/*|src/main/java/com/smarteye/service/*|src/main/java/com/smarteye/model/*|smarteye-lam-service/*|src/main/resources/application.yml|docker-compose*.yml|build.gradle)
            UPDATE_NEEDED=true
            echo "📁 Architecture change detected: $file"
            break
            ;;
    esac
done

if [ "$UPDATE_NEEDED" = true ]; then
    echo "🔄 Updating copilot instructions..."
    
    # Node.js 확인
    if ! command -v node &> /dev/null; then
        echo "⚠️  Node.js not found, skipping copilot instructions update"
        echo "   Install Node.js to enable automatic updates"
        exit 0
    fi
    
    # 업데이트 실행
    node scripts/update-copilot-instructions.js
    
    if [ $? -eq 0 ]; then
        # 변경사항이 있으면 스테이징에 추가
        if ! git diff --quiet .github/copilot-instructions.md; then
            git add .github/copilot-instructions.md
            echo "✅ Copilot instructions updated and staged"
        else
            echo "ℹ️  No changes needed in copilot instructions"
        fi
    else
        echo "❌ Failed to update copilot instructions"
        echo "   Commit will proceed without update"
    fi
else
    echo "ℹ️  No architecture changes detected, skipping update"
fi

echo "✅ Pre-commit hook completed"
exit 0
EOF

# 실행 권한 부여
chmod +x .git/hooks/pre-commit

echo "✅ Git hooks installed successfully!"
echo ""
echo "📌 What was installed:"
echo "   • Pre-commit hook: Automatically updates copilot instructions"
echo "   • Triggers on: Controller, Service, Model, LAM service, Config changes"
echo ""
echo "🔄 To manually update instructions anytime:"
echo "   ./scripts/update-instructions.sh"
echo ""
echo "🚫 To uninstall hooks:"
echo "   rm .git/hooks/pre-commit"

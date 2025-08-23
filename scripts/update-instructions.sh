#!/bin/bash

# SmartEye Copilot Instructions Updater
# Usage: ./scripts/update-instructions.sh [--commit] [--push]

echo "🤖 SmartEye Copilot Instructions Updater"
echo "========================================"

# Node.js 버전 확인
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is required but not installed."
    echo "Please install Node.js from https://nodejs.org/"
    exit 1
fi

# 프로젝트 루트로 이동
cd "$(dirname "$0")/.."

echo "📍 Working directory: $(pwd)"

# 업데이트 스크립트 실행
echo "🔄 Running update script..."
node scripts/update-copilot-instructions.js

UPDATE_EXIT_CODE=$?

if [ $UPDATE_EXIT_CODE -eq 0 ]; then
    echo "✅ Update completed successfully"
    
    # Git 상태 확인
    if git diff --quiet .github/copilot-instructions.md; then
        echo "📝 No changes detected in copilot instructions"
    else
        echo "📝 Changes detected in copilot instructions"
        
        # --commit 플래그가 있으면 자동 커밋
        if [ "$1" = "--commit" ] || [ "$2" = "--commit" ]; then
            echo "💾 Auto-committing changes..."
            git add .github/copilot-instructions.md
            git commit -m "📝 Update copilot instructions [auto-generated]"
            
            # --push 플래그가 있으면 자동 푸시
            if [ "$1" = "--push" ] || [ "$2" = "--push" ]; then
                echo "🚀 Auto-pushing changes..."
                git push
            fi
        else
            echo "💡 To commit these changes, run:"
            echo "   git add .github/copilot-instructions.md"
            echo "   git commit -m '📝 Update copilot instructions'"
            echo ""
            echo "Or run with --commit flag: ./scripts/update-instructions.sh --commit"
        fi
    fi
else
    echo "❌ Update failed with exit code $UPDATE_EXIT_CODE"
    exit 1
fi

echo ""
echo "🎉 Update process complete!"
echo "📄 Instructions file: .github/copilot-instructions.md"

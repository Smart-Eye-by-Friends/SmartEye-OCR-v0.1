// src/App.tsx
import React from 'react'
import MainLayout from './components/layout/MainLayout'
import './styles/variables.css'
import './styles/main.css'

const App: React.FC = () => {
  return (
    <div className="app">
      <header className="app-header">
        <h1>🔍 SmartEyeSsen 학습지 분석</h1>
        <p>AI 기반 학습지 OCR 및 구조 분석 시스템</p>
      </header>
      <MainLayout />
    </div>
  )
}

export default App

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { listenForClientErrors, rootErrorOptions } from './utils/clientErrors'

// 렌더 밖 스크립트 오류와 처리 안 된 비동기 오류도 오류 모음으로
listenForClientErrors()

createRoot(document.getElementById('root')!, rootErrorOptions).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

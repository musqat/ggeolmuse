import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { listenForChunkLoadErrors, listenForClientErrors, rootErrorOptions } from './utils/clientErrors'

// 렌더 밖 스크립트 오류와 처리 안 된 비동기 오류도 오류 모음으로
listenForClientErrors()
// 새 배포 뒤 옛 화면 조각을 못 받으면 한 번 새로고침
listenForChunkLoadErrors()

createRoot(document.getElementById('root')!, rootErrorOptions).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

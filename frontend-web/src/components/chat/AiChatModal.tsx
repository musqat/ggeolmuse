import React, { useState, useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';
import { X, Send, Maximize2, Minimize2, Trash2 } from 'lucide-react';
import { aiChatApi } from '../../services/aiChatApi';
import { useAuth } from '../../contexts/AuthContext';
import { useAiChat } from '../../contexts/AiChatContext';
import { loadChatHistory, saveChatHistory, clearChatHistory, type ChatTurn } from '../../utils/aiChatHistory';

interface AiChatModalProps {
  onRequireLogin: () => void;
}

// **굵게**만 <strong>으로 렌더, 나머지는 평문
function renderWithBold(text: string): React.ReactNode[] {
  return text.split(/(\*\*.+?\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') ? (
      <strong key={i}>{part.slice(2, -2)}</strong>
    ) : (
      <React.Fragment key={i}>{part}</React.Fragment>
    )
  );
}

const AiChatModal: React.FC<AiChatModalProps> = ({ onRequireLogin }) => {
  const { isAuthenticated } = useAuth();
  const { isOpen, autoSymbol, closeChat, consumeAutoSymbol } = useAiChat();
  const [input, setInput] = useState('');
  const [turns, setTurns] = useState<ChatTurn[]>(() => loadChatHistory());
  const [loading, setLoading] = useState(false);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState(false);
  const bodyRef = useRef<HTMLDivElement>(null);

  // 대화 변경 시 localStorage 저장
  useEffect(() => {
    saveChatHistory(turns);
  }, [turns]);

  // 새 메시지 시 맨 아래로 스크롤
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight });
  }, [turns, loading]);

  const send = async (message: string, symbol?: string) => {
    const msg = message.trim();
    if (!msg || loading) return;
    setError('');
    setTurns((t) => [...t, { role: 'user', text: msg }]);
    setLoading(true);
    try {
      const res = await aiChatApi.sendMessage(msg, symbol);
      setTurns((t) => [...t, { role: 'ai', text: res.data.answer }]);
      setRemaining(res.data.remaining);
    } catch (e: any) {
      if (e.response?.status === 429) {
        setError('오늘 사용 한도(5회)를 모두 사용했습니다.');
      } else {
        setError('분석 요청에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setLoading(false);
    }
  };

  // 차트 등에서 종목 지정해 열면 자동 분석 (symbol 직접 전달 → mini 추출 생략, 신규종목도 분석 가능)
  useEffect(() => {
    if (isOpen && autoSymbol && isAuthenticated && !loading) {
      send(`${autoSymbol} 분석해줘`, autoSymbol);
      consumeAutoSymbol();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, autoSymbol, isAuthenticated]);

  if (!isOpen) return null;

  const handleSend = () => {
    const msg = input;
    setInput('');
    send(msg);
  };

  const handleClear = () => {
    setTurns([]);
    clearChatHistory();
    setRemaining(null);
    setError('');
  };

  return ReactDOM.createPortal(
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 9999, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-end', padding: '24px' }}
      onClick={closeChat}
    >
      <div
        className={`bg-surface rounded-2xl shadow-xl w-full flex flex-col ${expanded ? 'max-w-2xl' : 'max-w-md'}`}
        style={{ height: expanded ? '88vh' : '70vh' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-line">
          <span className="font-bold text-tx-1">AI 종목 분석</span>
          <div className="flex items-center gap-1">
            {turns.length > 0 && (
              <button onClick={handleClear} title="기록 지우기" className="text-tx-3 hover:text-tx-1 p-1">
                <Trash2 className="w-4 h-4" />
              </button>
            )}
            <button
              onClick={() => setExpanded((v) => !v)}
              title={expanded ? '작게 보기' : '크게 보기'}
              className="text-tx-3 hover:text-tx-1 p-1"
            >
              {expanded ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
            </button>
            <button onClick={closeChat} className="text-tx-3 hover:text-tx-1 p-1">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* 본문 */}
        <div ref={bodyRef} className="flex-1 overflow-y-auto p-4 space-y-3">
          {!isAuthenticated ? (
            <div className="text-center py-10">
              <p className="text-tx-2 mb-4">로그인하면 AI 주식 기술 분석을 받아볼 수 있어요.</p>
              <button
                onClick={onRequireLogin}
                className="bg-brand text-white px-4 py-2 rounded-lg hover:bg-brand-dark"
              >
                로그인하기
              </button>
            </div>
          ) : turns.length === 0 ? (
            <div className="text-xs text-tx-3 leading-relaxed">
              <p className="mb-2">
                종목의 기술적 지표(이동평균, RSI, MACD 등)를 바탕으로 차트 흐름을 설명합니다.
              </p>
              <ul className="list-disc pl-4 space-y-1">
                <li>한 번에 한 종목만 분석할 수 있습니다 (예: AAPL)</li>
                <li>투자 자문/권유가 아닙니다</li>
                <li>펀더멘털·뉴스·실적은 반영하지 않습니다</li>
                <li>과거 데이터 기반이며 미래 수익을 보장하지 않습니다</li>
                <li>대화 기록은 이 브라우저에만 임시 저장됩니다 (영구 저장 아님)</li>
              </ul>
            </div>
          ) : (
            turns.map((t, i) => (
              <div
                key={i}
                className={`text-sm whitespace-pre-wrap p-3 rounded-lg ${
                  t.role === 'user' ? 'bg-brand-bg text-tx-1 ml-8' : 'bg-elevated text-tx-1 mr-8'
                }`}
              >
                {t.role === 'ai' ? renderWithBold(t.text) : t.text}
              </div>
            ))
          )}
          {loading && <div className="text-sm text-tx-3">분석 중...</div>}
          {error && <div className="text-sm text-red-500">{error}</div>}
        </div>

        {/* 입력 */}
        {isAuthenticated && (
          <div className="p-3 border-t border-line">
            {remaining !== null && (
              <div className="text-xs text-tx-3 mb-2">오늘 남은 횟수: {remaining}회</div>
            )}
            <div className="flex gap-2">
              <input
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                placeholder="예: AAPL 어때?"
                className="flex-1 px-3 py-2 border border-line-strong rounded-lg focus:outline-none focus:ring-2 focus:ring-brand"
                disabled={loading}
              />
              <button
                onClick={handleSend}
                disabled={loading}
                className="bg-brand text-white px-3 rounded-lg hover:bg-brand-dark disabled:opacity-50"
              >
                <Send className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>,
    document.body
  );
};

export default AiChatModal;

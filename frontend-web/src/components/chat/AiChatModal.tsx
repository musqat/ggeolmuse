import React, { useState, useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';
import { X, Send, Maximize2, Minimize2 } from 'lucide-react';
import { aiChatApi } from '../../services/aiChatApi';
import { useAuth } from '../../hooks/useAuth';
import { useAiChat } from '../../hooks/useAiChat';
import { loadChatSession, saveChatSession, type ChatSession } from '../../utils/aiChatHistory';
import { getApiErrorCode, getApiErrorMessage, getApiErrorStatus } from '../../utils/apiError';

interface AiChatModalProps {
  onRequireLogin: () => void;
}

const SYMBOL_PLACEHOLDER = '종목 코드를 입력해주세요 (예: AAPL)';
const QUESTION_PLACEHOLDER = '예: RSI 어때? · 1년 추이는? · 배당은?';

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

// 오류 문구. 이유 코드가 있으면 서버 문구, 없으면(게이트웨이 대체 응답 등) 상태 코드로 고른다
function errorText(e: unknown): string {
  if (getApiErrorCode(e)) return getApiErrorMessage(e, '요청에 실패했습니다.');
  const status = getApiErrorStatus(e);
  if (status === 429) return '오늘 사용 한도(5회)를 모두 사용했습니다.';
  if (status === 404 || status === 503) return 'AI 분석 기능은 현재 환경에서 사용할 수 없습니다.';
  return '분석 요청에 실패했습니다. 잠시 후 다시 시도해주세요.';
}

const AiChatModal: React.FC<AiChatModalProps> = ({ onRequireLogin }) => {
  const { isAuthenticated, isLoading: authLoading } = useAuth();
  const { isOpen, autoSymbol, closeChat, consumeAutoSymbol } = useAiChat();
  const [session, setSession] = useState<ChatSession | null>(() => loadChatSession());
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState(false);
  const bodyRef = useRef<HTMLDivElement>(null);

  // 대화 변경 시 localStorage 저장
  useEffect(() => {
    saveChatSession(session);
  }, [session]);

  // 로그인이 풀리면 대화를 지운다. 같은 브라우저의 다음 사용자가 보지 않게. 로그인 확인 중에는 기다린다
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      setSession(null);
      setRemaining(null);
      setError('');
    }
  }, [authLoading, isAuthenticated]);

  // 새 메시지 시 맨 아래로 스크롤
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight });
  }, [session, loading]);

  // 종목을 확인하고 대화를 연다. 한도는 쓰지 않는다
  const start = async (rawSymbol: string) => {
    const symbol = rawSymbol.trim();
    if (!symbol || loading) return;
    setError('');
    setLoading(true);
    try {
      const res = await aiChatApi.startConversation(symbol);
      setSession({ conversationId: res.data.conversation_id, symbol: res.data.symbol, name: res.data.name, turns: [] });
      setRemaining(res.data.remaining);
    } catch (e: unknown) {
      setError(errorText(e));
    } finally {
      setLoading(false);
    }
  };

  const ask = async (message: string) => {
    const msg = message.trim();
    if (!msg || loading || !session) return;
    const current = session;
    setError('');
    setSession({ ...current, turns: [...current.turns, { role: 'user', text: msg }] });
    setLoading(true);
    try {
      const res = await aiChatApi.ask(current.conversationId, msg);
      // 기다리는 동안 종목을 바꿨으면 옛 대화의 답은 버린다
      setSession((s) =>
        s && s.conversationId === current.conversationId
          ? { ...s, turns: [...s.turns, { role: 'ai', text: res.data.answer }] }
          : s
      );
      setRemaining(res.data.remaining);
    } catch (e: unknown) {
      if (getApiErrorCode(e) === 'conversation_not_found') setSession(null);
      setError(errorText(e));
    } finally {
      setLoading(false);
    }
  };

  // 차트 등에서 종목을 지정해 열면 그 종목으로 대화를 시작한다. 질문은 보내지 않는다
  useEffect(() => {
    if (isOpen && autoSymbol && isAuthenticated && !loading) {
      if (session?.symbol !== autoSymbol.toUpperCase()) start(autoSymbol);
      consumeAutoSymbol();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, autoSymbol, isAuthenticated, loading]);

  if (!isOpen) return null;

  const handleSend = () => {
    const text = input;
    setInput('');
    if (session) ask(text);
    else start(text);
  };

  const changeSymbol = () => {
    setSession(null);
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

        {/* 대화 중인 종목 */}
        {isAuthenticated && session && (
          <div className="flex items-center justify-between px-4 py-2 border-b border-line text-sm">
            <span className="font-semibold text-tx-1">
              {session.symbol}
              {session.name ? ` · ${session.name}` : ''}
            </span>
            <button
              onClick={changeSymbol}
              disabled={loading}
              className="text-xs text-tx-3 hover:text-tx-1 disabled:opacity-50"
            >
              종목 바꾸기
            </button>
          </div>
        )}

        {/* 본문 */}
        <div ref={bodyRef} className="flex-1 overflow-y-auto p-4 space-y-3">
          {!isAuthenticated ? (
            <div className="text-center py-10">
              <p className="text-tx-2 mb-4">로그인하면 AI 종목 분석을 받아볼 수 있어요.</p>
              <button
                onClick={onRequireLogin}
                className="bg-brand text-brand-ink px-4 py-2 rounded-lg hover:bg-brand-dark"
              >
                로그인하기
              </button>
            </div>
          ) : !session || session.turns.length === 0 ? (
            <div className="text-xs text-tx-3 leading-relaxed">
              <p className="mb-2">
                {session
                  ? `${session.symbol}의 기술 지표, 주가 추이, 배당을 물어보세요.`
                  : '종목을 정하면 그 종목의 기술 지표, 주가 추이, 배당을 물어볼 수 있습니다.'}
              </p>
              <ul className="list-disc pl-4 space-y-1">
                <li>한 번에 한 종목만 다룹니다 (예: AAPL)</li>
                <li>투자 자문/권유가 아닙니다</li>
                <li>실적·뉴스는 반영하지 않습니다</li>
                <li>과거 데이터 기반이며 미래 수익을 보장하지 않습니다</li>
                <li>대화 기록은 이 브라우저에만 임시 저장됩니다 (영구 저장 아님)</li>
              </ul>
            </div>
          ) : (
            session.turns.map((t, i) => (
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
          {loading && <div className="text-sm text-tx-3">{session ? '분석 중...' : '종목 확인 중...'}</div>}
          {error && <div className="text-sm text-danger">{error}</div>}
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
                placeholder={session ? QUESTION_PLACEHOLDER : SYMBOL_PLACEHOLDER}
                className="flex-1 px-3 py-2 border border-line-strong rounded-lg focus:outline-none focus:ring-2 focus:ring-brand"
                disabled={loading}
              />
              <button
                onClick={handleSend}
                disabled={loading}
                aria-label="보내기"
                className="bg-brand text-brand-ink px-3 rounded-lg hover:bg-brand-dark disabled:opacity-50"
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

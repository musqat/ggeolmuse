import React, { useState, useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';
import { X, Send, Maximize2, Minimize2 } from 'lucide-react';
import { aiChatApi } from '../../services/aiChatApi';
import { useAuth } from '../../hooks/useAuth';
import { useAiChat } from '../../hooks/useAiChat';
import { loadChatSession, saveChatSession, type ChatSession } from '../../utils/aiChatHistory';
import { getApiErrorCode, getApiErrorMessage, getApiErrorStatus } from '../../utils/apiError';
import { LAYER } from '@/styles/layers';

interface AiChatModalProps {
  onRequireLogin: () => void;
}

const SYMBOL_PLACEHOLDER = '종목 코드를 입력해주세요 (예: AAPL)';
const QUESTION_PLACEHOLDER = '예: RSI 어때? · 1년 추이는? · 배당은?';
// 입력 길이 제한(서버와 같게 종목 코드 16자 · 질문 500자)
const SYMBOL_MAX_LENGTH = 16;
const QUESTION_MAX_LENGTH = 500;

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

// 오류 문구(이유 코드가 있으면 서버 문구, 없으면 상태 코드로)
function errorText(e: unknown): string {
  if (getApiErrorCode(e)) return getApiErrorMessage(e, '요청에 실패했습니다.');
  const status = getApiErrorStatus(e);
  if (status === 422) return '입력 내용을 확인해 주세요.';
  if (status === 429) return '오늘 사용 한도(5회)를 모두 사용했습니다.';
  if (status === 404 || status === 503) return 'AI 분석 기능은 현재 환경에서 사용할 수 없습니다.';
  return '분석 요청에 실패했습니다. 잠시 후 다시 시도해주세요.';
}

const AiChatModal: React.FC<AiChatModalProps> = ({ onRequireLogin }) => {
  const { isAuthenticated, isLoading: authLoading } = useAuth();
  const { isOpen, autoSymbol, closeChat, consumeAutoSymbol } = useAiChat();
  const [session, setSession] = useState<ChatSession | null>(() => loadChatSession());
  const [input, setInput] = useState('');
  // 기다리는 요청(start 는 종목 확인, ask 는 답)
  const [pending, setPending] = useState<'start' | 'ask' | null>(null);
  // 답을 기다리는 동안 차트에서 고른 종목(답 뒤 바꾸기 버튼으로, 새 대화 · 다음 질문이면 거둠)
  const [nextSymbol, setNextSymbol] = useState<string | null>(null);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState(false);
  const bodyRef = useRef<HTMLDivElement>(null);
  const loading = pending !== null;

  // 대화 변경 시 localStorage 저장
  useEffect(() => {
    saveChatSession(session);
  }, [session]);

  // 로그인이 풀리면 대화 지우기(같은 브라우저의 다음 사용자가 못 보게, 확인 중에는 기다림)
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      setSession(null);
      setRemaining(null);
      setError('');
    }
  }, [authLoading, isAuthenticated]);

  // 남은 횟수를 모르면 열 때 묻기(실패하면 다음 응답까지 숨김)
  useEffect(() => {
    if (!isOpen || !isAuthenticated || remaining !== null) return;
    let stale = false;
    aiChatApi
      .quota()
      .then((res) => {
        if (!stale) setRemaining(res.data.remaining);
      })
      .catch(() => {});
    return () => {
      stale = true;
    };
  }, [isOpen, isAuthenticated, remaining]);

  // 새 메시지 시 맨 아래로 스크롤
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight });
  }, [session, pending, nextSymbol]);

  // 종목 확인과 대화 열기(한도 차감 없음)
  // 대화 중 다른 종목으로 바꾸다 실패하면 지금 대화를 두고 오류 앞에 그 종목 표시
  const start = async (rawSymbol: string) => {
    const symbol = rawSymbol.trim();
    if (!symbol || loading) return;
    const switching = session !== null;
    setError('');
    setNextSymbol(null);
    setPending('start');
    try {
      const res = await aiChatApi.startConversation(symbol);
      setSession({
        conversationId: res.data.conversation_id,
        symbol: res.data.symbol,
        name: res.data.name,
        turns: [],
        updatedAt: Date.now(),
      });
      setRemaining(res.data.remaining);
    } catch (e: unknown) {
      setError(switching ? `${symbol.toUpperCase()}: ${errorText(e)}` : errorText(e));
    } finally {
      setPending(null);
    }
  };

  const ask = async (message: string) => {
    const msg = message.trim();
    if (!msg || loading || !session) return;
    const current = session;
    setError('');
    setNextSymbol(null);
    setSession({ ...current, turns: [...current.turns, { role: 'user', text: msg }] });
    setPending('ask');
    try {
      const res = await aiChatApi.ask(current.conversationId, msg);
      // 기다리는 동안 대화가 지워졌거나(로그아웃) 대화 ID 가 다르면 답을 버림
      setSession((s) =>
        s && s.conversationId === current.conversationId
          ? { ...s, turns: [...s.turns, { role: 'ai', text: res.data.answer }], updatedAt: Date.now() }
          : s
      );
      setRemaining(res.data.remaining);
    } catch (e: unknown) {
      if (getApiErrorCode(e) === 'conversation_not_found') setSession(null);
      setError(errorText(e));
    } finally {
      setPending(null);
    }
  };

  // 종목을 지정해 열면 그 종목으로 대화 시작(질문은 안 보냄)
  // 답을 기다리는 중이면 바로 바꾸지 않고 답 뒤 바꾸기 버튼으로
  useEffect(() => {
    if (!isOpen || !autoSymbol || !isAuthenticated || pending === 'start') return;
    const symbol = autoSymbol.trim().toUpperCase();
    consumeAutoSymbol();
    if (session?.symbol === symbol) return;
    if (pending === 'ask') setNextSymbol(symbol);
    else start(symbol);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, autoSymbol, isAuthenticated, pending]);

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
      style={{ position: 'fixed', inset: 0, zIndex: LAYER.modal, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-end', padding: '24px' }}
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
            <button onClick={closeChat} aria-label="닫기" className="text-tx-3 hover:text-tx-1 p-1">
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
          {pending && (
            <div className="text-sm text-tx-3">{pending === 'start' ? '종목 확인 중...' : '분석 중...'}</div>
          )}
          {session && nextSymbol && (
            <button
              onClick={() => start(nextSymbol)}
              disabled={loading}
              className="text-sm px-3 py-1.5 border border-line-strong text-tx-1 rounded-lg hover:bg-elevated disabled:opacity-50"
            >
              {nextSymbol} 대화로 바꾸기
            </button>
          )}
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
                maxLength={session ? QUESTION_MAX_LENGTH : SYMBOL_MAX_LENGTH}
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

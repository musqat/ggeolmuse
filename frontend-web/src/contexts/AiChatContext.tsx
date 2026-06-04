import React, { createContext, useContext, useState, useCallback } from 'react';

interface AiChatContextValue {
  isOpen: boolean;
  // 자동 분석할 종목 (버튼으로 열 때 설정). null이면 일반 채팅.
  autoSymbol: string | null;
  openChat: (symbol?: string) => void;
  closeChat: () => void;
  // 자동 분석 소비 후 초기화 (모달이 한 번 처리하고 비움)
  consumeAutoSymbol: () => void;
}

const AiChatContext = createContext<AiChatContextValue | undefined>(undefined);

export const AiChatProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [autoSymbol, setAutoSymbol] = useState<string | null>(null);

  const openChat = useCallback((symbol?: string) => {
    setAutoSymbol(symbol ?? null);
    setIsOpen(true);
  }, []);

  const closeChat = useCallback(() => setIsOpen(false), []);

  const consumeAutoSymbol = useCallback(() => setAutoSymbol(null), []);

  return (
    <AiChatContext.Provider value={{ isOpen, autoSymbol, openChat, closeChat, consumeAutoSymbol }}>
      {children}
    </AiChatContext.Provider>
  );
};

export const useAiChat = (): AiChatContextValue => {
  const ctx = useContext(AiChatContext);
  if (!ctx) throw new Error('useAiChat must be used within AiChatProvider');
  return ctx;
};

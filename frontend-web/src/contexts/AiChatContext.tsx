import React, { useState, useCallback } from 'react';
import { AiChatContext } from '../hooks/useAiChat';

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

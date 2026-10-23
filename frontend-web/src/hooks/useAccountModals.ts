import { useState } from 'react';

export type AccountModal = 'create' | 'deposit' | 'exchange' | 'delete';

// 계좌 화면에서 열린 창 하나와 그 창이 다루는 계좌
export const useAccountModals = () => {
  const [open, setOpen] = useState<AccountModal | null>(null);
  const [accountId, setAccountId] = useState<number | null>(null);

  const show = (modal: AccountModal, id?: number) => {
    setOpen(modal);
    setAccountId(id ?? null);
  };

  const close = () => {
    setOpen(null);
    setAccountId(null);
  };

  return { open, accountId, show, close };
};

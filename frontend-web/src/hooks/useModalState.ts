import { useState } from 'react';

interface UseModalStateReturn {
  // 모달 상태
  showCreateModal: boolean;
  showDepositModal: boolean;
  showExchangeModal: boolean;
  showDeleteModal: boolean;
  selectedAccountId: number | null;

  // 모달 열기 핸들러
  openCreateModal: () => void;
  openDepositModal: (accountId: number) => void;
  openExchangeModal: (accountId: number) => void;
  openDeleteModal: (accountId: number) => void;

  // 모달 닫기 핸들러
  closeCreateModal: () => void;
  closeDepositModal: () => void;
  closeExchangeModal: () => void;
  closeDeleteModal: () => void;

  // 선택된 계좌 초기화
  clearSelectedAccount: () => void;
}

// 계좌 모달들의 열림 상태와 선택한 계좌
export const useModalState = (): UseModalStateReturn => {
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showDepositModal, setShowDepositModal] = useState(false);
  const [showExchangeModal, setShowExchangeModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);

  const openCreateModal = () => {
    setShowCreateModal(true);
  };

  const openDepositModal = (accountId: number) => {
    setSelectedAccountId(accountId);
    setShowDepositModal(true);
  };

  const openExchangeModal = (accountId: number) => {
    setSelectedAccountId(accountId);
    setShowExchangeModal(true);
  };

  const openDeleteModal = (accountId: number) => {
    setSelectedAccountId(accountId);
    setShowDeleteModal(true);
  };

  const closeCreateModal = () => {
    setShowCreateModal(false);
  };

  const closeDepositModal = () => {
    setShowDepositModal(false);
    setSelectedAccountId(null);
  };

  const closeExchangeModal = () => {
    setShowExchangeModal(false);
    setSelectedAccountId(null);
  };

  const closeDeleteModal = () => {
    setShowDeleteModal(false);
    setSelectedAccountId(null);
  };

  const clearSelectedAccount = () => {
    setSelectedAccountId(null);
  };

  return {
    // 모달 상태
    showCreateModal,
    showDepositModal,
    showExchangeModal,
    showDeleteModal,
    selectedAccountId,

    // 모달 열기
    openCreateModal,
    openDepositModal,
    openExchangeModal,
    openDeleteModal,

    // 모달 닫기
    closeCreateModal,
    closeDepositModal,
    closeExchangeModal,
    closeDeleteModal,

    // 선택 초기화
    clearSelectedAccount
  };
};

import { useState } from 'react';

export interface UseModalStateReturn {
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

/**
 * 모달 상태 관리 훅
 */
export const useModalState = (): UseModalStateReturn => {
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showDepositModal, setShowDepositModal] = useState(false);
  const [showExchangeModal, setShowExchangeModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);

  /**
   * 계좌 생성 모달 열기
   */
  const openCreateModal = () => {
    setShowCreateModal(true);
  };

  /**
   * 입금 모달 열기
   * @param {number} accountId - 입금할 계좌 ID
   */
  const openDepositModal = (accountId: number) => {
    setSelectedAccountId(accountId);
    setShowDepositModal(true);
  };

  /**
   * 환전 모달 열기
   * @param {number} accountId - 환전할 계좌 ID
   */
  const openExchangeModal = (accountId: number) => {
    setSelectedAccountId(accountId);
    setShowExchangeModal(true);
  };

  /**
   * 계좌 삭제 모달 열기
   * @param {number} accountId - 삭제할 계좌 ID
   */
  const openDeleteModal = (accountId: number) => {
    setSelectedAccountId(accountId);
    setShowDeleteModal(true);
  };

  /**
   * 계좌 생성 모달 닫기
   */
  const closeCreateModal = () => {
    setShowCreateModal(false);
  };

  /**
   * 입금 모달 닫기
   */
  const closeDepositModal = () => {
    setShowDepositModal(false);
    setSelectedAccountId(null);
  };

  /**
   * 환전 모달 닫기
   */
  const closeExchangeModal = () => {
    setShowExchangeModal(false);
    setSelectedAccountId(null);
  };

  /**
   * 계좌 삭제 모달 닫기
   */
  const closeDeleteModal = () => {
    setShowDeleteModal(false);
    setSelectedAccountId(null);
  };

  /**
   * 선택된 계좌 ID 초기화
   */
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

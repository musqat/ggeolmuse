import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  RefreshCw,
  Lock,
  LogIn
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import LoginModal from '../components/auth/LoginModal';

// Hook imports
import { useAccountData } from '../hooks/useAccountData';
import { useAccountOperations } from '../hooks/useAccountOperations';
import { useModalState } from '../hooks/useModalState';

// Component imports
import { AccountPageHeader } from '../components/account/AccountPageHeader';
import { TotalAssetsSummary } from '../components/account/TotalAssetsSummary';
import { AccountCard } from '../components/account/AccountCard';
import CreateAccountModal from '../components/account/modals/CreateAccountModal';
import DepositModal from '../components/account/modals/DepositModal';
import ExchangeModal from '../components/account/modals/ExchangeModal';
import DeleteAccountModal from '../components/account/modals/DeleteAccountModal';

const Account: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated, login } = useAuth();

  const [hideBalances, setHideBalances] = useState(false);
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  // Custom hooks for data and operations
  const {
    accounts,
    accountBalances,
    portfolioSummary,
    currentExchangeRate,
    loading,
    error,
    refetch
  } = useAccountData(isAuthenticated);

  const {
    createAccount,
    depositKrw,
    exchangeCurrency,
    deleteAccount,
    loading: operationLoading
  } = useAccountOperations(refetch);

  const {
    showCreateModal,
    showDepositModal,
    showExchangeModal,
    showDeleteModal,
    selectedAccountId,
    openCreateModal,
    openDepositModal,
    openExchangeModal,
    openDeleteModal,
    closeCreateModal,
    closeDepositModal,
    closeExchangeModal,
    closeDeleteModal
  } = useModalState();

  // 총 자산 계산 (현금 + 주식 평가금액)
  const calculateTotalAssets = (): number => {
    if (!currentExchangeRate) return 0;

    // 1. 모든 계좌의 현금 합계 (KRW + USD를 KRW로 환산)
    let cashTotal = 0;
    accountBalances.forEach((balance) => {
      cashTotal += balance.balanceKrw + (balance.balanceUsd * currentExchangeRate);
    });

    // 2. 주식 평가금액 (USD를 KRW로 환산)
    const stockValue = portfolioSummary && portfolioSummary.totalCurrentValue
      ? portfolioSummary.totalCurrentValue * currentExchangeRate
      : 0;

    return cashTotal + stockValue;
  };

  // 잔액 포맷팅
  const formatBalance = (amount: number, currency: 'KRW' | 'USD') => {
    if (hideBalances) {
      return currency === 'KRW' ? '₩***,***' : '$***.**';
    }

    const symbol = currency === 'KRW' ? '₩' : '$';
    const formatted = amount.toLocaleString(undefined, {
      minimumFractionDigits: currency === 'USD' ? 2 : 0,
      maximumFractionDigits: currency === 'USD' ? 2 : 0
    });

    return `${symbol}${formatted}`;
  };

  // 로그인하지 않은 사용자
  if (!isAuthenticated) {
    return (
      <>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="min-h-[60vh] flex items-center justify-center">
            <div className="text-center">
              <Lock className="w-16 h-16 text-brand mx-auto mb-4" />
              <h1 className="text-3xl font-bold text-tx-1 mb-4">로그인이 필요한 서비스입니다</h1>
              <p className="text-lg text-tx-2 mb-6">
                계좌 관리 기능을 이용하시려면 먼저 로그인해주세요
              </p>
              <button
                onClick={() => setIsLoginModalOpen(true)}
                className="flex items-center space-x-2 bg-brand text-white px-6 py-3 rounded-lg hover:bg-brand-dark transition-colors mx-auto"
              >
                <LogIn className="w-5 h-5" />
                <span>로그인하기</span>
              </button>
            </div>
          </div>
        </div>
        <LoginModal
          isOpen={isLoginModalOpen}
          onClose={() => setIsLoginModalOpen(false)}
          onSwitchToSignup={() => {
            setIsLoginModalOpen(false);
            // 회원가입은 Header에서 관리되므로 단순히 모달만 닫음
          }}
          onLogin={async (email: string, password: string) => {
            await login(email, password);
          }}
        />
      </>
    );
  }

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <RefreshCw className="w-8 h-8 animate-spin text-brand" />
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 헤더 */}
        <AccountPageHeader
          hideBalances={hideBalances}
          onToggleBalances={() => setHideBalances(!hideBalances)}
          onCreateAccount={openCreateModal}
        />

        {error && (
          <div className="bg-red-500/100/10 border border-red-500/25 text-red-600 px-4 py-3 rounded-lg">
            {error}
          </div>
        )}

        {/* 총 자산 요약 */}
        <TotalAssetsSummary
          totalAssets={calculateTotalAssets()}
          accountCount={accounts.length}
          currentExchangeRate={currentExchangeRate}
          hideBalances={hideBalances}
          formatBalance={formatBalance}
        />

        {/* 계좌 목록 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {accounts.map((account) => {
            const balance = accountBalances.get(account.accountId);
            if (!balance) return null;

            return (
              <AccountCard
                key={account.accountId}
                account={account}
                balance={balance}
                hideBalances={hideBalances}
                formatBalance={formatBalance}
                onDeposit={() => openDepositModal(account.accountId)}
                onExchange={() => openExchangeModal(account.accountId)}
                onDelete={() => openDeleteModal(account.accountId)}
              />
            );
          })}
        </div>

        {/* 빈 상태 */}
        {accounts.length === 0 && !error && (
          <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-12 text-center">
            <h3 className="text-lg font-semibold text-tx-1 mb-2">계좌가 없습니다</h3>
            <p className="text-tx-2 mb-6">첫 번째 계좌를 생성하여 투자를 시작해보세요</p>
            <button
              onClick={openCreateModal}
              className="px-6 py-3 bg-brand text-white rounded-lg hover:bg-brand-dark transition-colors"
            >
              계좌 생성하기
            </button>
          </div>
        )}
      </div>

      {/* Modals */}
      <CreateAccountModal
        isOpen={showCreateModal}
        onClose={closeCreateModal}
        onSubmit={async (accountName: string, commissionRate: number) => {
          await createAccount({ accountName, commissionRate });
        }}
      />

      <DepositModal
        isOpen={showDepositModal}
        onClose={closeDepositModal}
        onSubmit={async (amount: number) => {
          if (selectedAccountId) {
            await depositKrw({ accountId: selectedAccountId, amount });
          }
        }}
        accountId={selectedAccountId}
      />

      <ExchangeModal
        isOpen={showExchangeModal}
        onClose={closeExchangeModal}
        onSubmit={async (params) => {
          if (selectedAccountId) {
            await exchangeCurrency({
              accountId: selectedAccountId,
              fromCurrency: params.fromCurrency,
              amount: params.originalAmount,
              exchangeRate: params.exchangeRate
            });
          }
        }}
        accountId={selectedAccountId}
        accountBalance={selectedAccountId ? accountBalances.get(selectedAccountId) : undefined}
        currentExchangeRate={currentExchangeRate}
      />

      <DeleteAccountModal
        isOpen={showDeleteModal}
        onClose={closeDeleteModal}
        onConfirm={async () => {
          if (selectedAccountId) {
            await deleteAccount({ accountId: selectedAccountId });
          }
        }}
        accountId={selectedAccountId}
      />
    </div>
  );
};

export default Account;

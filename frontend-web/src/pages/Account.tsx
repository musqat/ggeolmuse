import React, { useState } from 'react';
import { RefreshCw, LogIn } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { useAuthModal } from '../hooks/useAuthModal';

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
import { formatBalanceAmount } from '../utils/gainLoss';

const Account: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const { openLogin } = useAuthModal();

  const [hideBalances, setHideBalances] = useState(false);

  // Custom hooks for data and operations
  const {
    accounts,
    accountBalances,
    currentExchangeRate,
    totalAssets,
    loading,
    error,
    refetch
  } = useAccountData(isAuthenticated);

  const {
    createAccount,
    depositKrw,
    exchangeCurrency,
    deleteAccount
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

  // 잔액 포맷팅
  const formatBalance = (amount: number, currency: 'KRW' | 'USD') => {
    if (hideBalances) {
      return currency === 'KRW' ? '₩***,***' : '$***.**';
    }

    return formatBalanceAmount(amount, currency);
  };

  // 로그인하지 않은 사용자
  if (!isAuthenticated) {
    return (
      <>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="min-h-[60vh] flex items-center justify-center">
            <div className="text-center">
              <h1 className="text-3xl font-bold text-tx-1 mb-4">로그인이 필요한 서비스입니다</h1>
              <p className="text-lg text-tx-2 mb-6">
                계좌 관리 기능을 이용하시려면 먼저 로그인해주세요
              </p>
              <button
                onClick={openLogin}
                className="flex items-center space-x-2 bg-brand text-brand-ink px-6 py-3 rounded-lg hover:bg-brand-dark transition-colors mx-auto"
              >
                <LogIn className="w-5 h-5" />
                <span>로그인하기</span>
              </button>
            </div>
          </div>
        </div>
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
          <div className="bg-danger/10 border border-danger/25 text-danger px-4 py-3 rounded-lg">
            {error}
          </div>
        )}

        {/* 총 자산 요약 */}
        <TotalAssetsSummary
          totalAssets={totalAssets}
          accountCount={accounts.length}
          currentExchangeRate={currentExchangeRate}
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
              className="px-6 py-3 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark transition-colors"
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
        onSubmit={async (accountName: string, commissionRate: number, slippageRate: number) => {
          await createAccount({ accountName, commissionRate, slippageRate });
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
      />
    </div>
  );
};

export default Account;

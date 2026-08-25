import React, { useState } from 'react';
import { Modal } from '@/components/common/Modal';
import type { AccountBalance } from '@/services/api';

interface ExchangeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (params: {
    fromCurrency: 'KRW' | 'USD';
    toCurrency: 'KRW' | 'USD';
    originalAmount: number;
    exchangeRate: number;
  }) =>Promise<void>;
  accountId: number | null;
  accountBalance: AccountBalance | undefined;
  currentExchangeRate: number;
}

/**
 * 환전 모달 컴포넌트
 * KRW ↔ USD 환전을 처리합니다.
 * 현재 환율 또는 수동 환율을 선택할 수 있습니다.
 */
const ExchangeModal: React.FC<ExchangeModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  accountId,
  accountBalance,
  currentExchangeRate,
}) => {
  const [exchangeFromCurrency, setExchangeFromCurrency] = useState<'KRW' | 'USD'>('KRW');
  const [exchangeAmount, setExchangeAmount] = useState('');
  const [useManualRate, setUseManualRate] = useState(false);
  const [manualExchangeRate, setManualExchangeRate] = useState('');

  const handleClose = () => {
    setExchangeAmount('');
    setExchangeFromCurrency('KRW');
    setUseManualRate(false);
    setManualExchangeRate('');
    onClose();
  };

  const handleSubmit = async () => {
    const amount = parseFloat(exchangeAmount);
    if (isNaN(amount) || amount <= 0) {
      alert('올바른 금액을 입력해주세요.');
      return;
    }

    // 환율 결정: 수동 입력 우선, 아니면 현재 환율 사용
    let exchangeRate: number;
    if (useManualRate) {
      exchangeRate = parseFloat(manualExchangeRate);
      if (isNaN(exchangeRate) || exchangeRate <= 0) {
        alert('올바른 환율을 입력해주세요.');
        return;
      }
    } else {
      if (!currentExchangeRate || currentExchangeRate <= 0) {
        alert('환율 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.');
        return;
      }
      exchangeRate = currentExchangeRate;
    }

    const toCurrency = exchangeFromCurrency === 'KRW' ? 'USD' : 'KRW';

    await onSubmit({
      fromCurrency: exchangeFromCurrency,
      toCurrency: toCurrency,
      originalAmount: amount,
      exchangeRate: exchangeRate,
    });

    handleClose();
  };

  // 예상 환전 금액 계산
  const calculateExpectedAmount = (): string | null => {
    if (!exchangeAmount || parseFloat(exchangeAmount) <= 0) return null;

    const displayRate = useManualRate && manualExchangeRate
      ? parseFloat(manualExchangeRate)
      : currentExchangeRate;

    if (!displayRate || displayRate <= 0) return null;

    const amount = parseFloat(exchangeAmount);
    if (exchangeFromCurrency === 'KRW') {
      return `$${(amount / displayRate).toFixed(2)}`;
    } else {
      return `₩${(amount * displayRate).toLocaleString()}`;
    }
  };

  const expectedAmount = calculateExpectedAmount();

  const footer = (
    <div className="flex space-x-3">
      <button
        onClick={handleClose}
        className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
      >
        취소
      </button>
      <button
        onClick={handleSubmit}
        disabled={!exchangeAmount || parseFloat(exchangeAmount) <= 0}
        className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        환전하기
      </button>
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="환전"
      footer={footer}
      maxWidth="md"
    >
      <div className="mb-4 p-3 bg-blue-50 rounded-lg">
        <p className="text-sm text-blue-800">
          현재 환율: ₩{currentExchangeRate.toLocaleString()}/USD
        </p>
      </div>

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">환전 방향</label>
          <select
            value={exchangeFromCurrency}
            onChange={(e) => setExchangeFromCurrency(e.target.value as 'KRW' | 'USD')}
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          >
            <option value="KRW">KRW → USD</option>
            <option value="USD">USD → KRW</option>
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">
            환전 금액 ({exchangeFromCurrency})
          </label>
          <input
            type="number"
            value={exchangeAmount}
            onChange={(e) => setExchangeAmount(e.target.value)}
            placeholder={`환전할 ${exchangeFromCurrency} 금액`}
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />

          {/* 빠른 금액 버튼 */}
          <div className="grid grid-cols-3 gap-2 mt-2">
            {exchangeFromCurrency === 'KRW' ? (
              <>
                <button
                  onClick={() => setExchangeAmount('1000000')}
                  className="py-2 px-3 bg-elevated text-tx-1 rounded-md hover:bg-hover transition-colors text-sm"
                >
                  $1천 (₩100만)
                </button>
                <button
                  onClick={() => setExchangeAmount('10000000')}
                  className="py-2 px-3 bg-elevated text-tx-1 rounded-md hover:bg-hover transition-colors text-sm"
                >
                  $1만 (₩1000만)
                </button>
                <button
                  onClick={() => {
                    if (accountBalance) {
                      setExchangeAmount(accountBalance.balanceKrw.toString());
                    }
                  }}
                  className="py-2 px-3 bg-brand-bg text-brand-dark rounded-md hover:bg-indigo-200 transition-colors text-sm font-medium"
                >
                  전액
                </button>
              </>
            ) : (
              <>
                <button
                  onClick={() => setExchangeAmount('1000')}
                  className="py-2 px-3 bg-elevated text-tx-1 rounded-md hover:bg-hover transition-colors text-sm"
                >
                  $1,000
                </button>
                <button
                  onClick={() => setExchangeAmount('10000')}
                  className="py-2 px-3 bg-elevated text-tx-1 rounded-md hover:bg-hover transition-colors text-sm"
                >
                  $10,000
                </button>
                <button
                  onClick={() => {
                    if (accountBalance) {
                      setExchangeAmount(accountBalance.balanceUsd.toString());
                    }
                  }}
                  className="py-2 px-3 bg-brand-bg text-brand-dark rounded-md hover:bg-indigo-200 transition-colors text-sm font-medium"
                >
                  전액
                </button>
              </>
            )}
          </div>
        </div>

        <div>
          <div className="flex items-center space-x-2 mb-2">
            <input
              type="checkbox"
              id="useManualRate"
              checked={useManualRate}
              onChange={(e) => setUseManualRate(e.target.checked)}
              className="w-4 h-4 text-brand border-line-strong rounded focus:ring-brand"
            />
            <label htmlFor="useManualRate" className="text-sm font-medium text-tx-1">
              수동 환율 입력
            </label>
          </div>
          {useManualRate && (
            <input
              type="number"
              value={manualExchangeRate}
              onChange={(e) => setManualExchangeRate(e.target.value)}
              placeholder="원하는 환율 입력 (예: 1350)"
              className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
            />
          )}
        </div>

        {expectedAmount && (
          <div className="p-3 bg-surface/50 rounded-lg">
            <p className="text-sm text-tx-1">
              예상 환전 금액: {expectedAmount}
            </p>
            {useManualRate && manualExchangeRate && parseFloat(manualExchangeRate) > 0 && (
              <p className="text-xs text-brand mt-1">
                수동 환율: ₩{parseFloat(manualExchangeRate).toLocaleString()}/USD
              </p>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
};

export default ExchangeModal;

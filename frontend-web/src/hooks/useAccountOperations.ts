import { useState } from 'react';
import { accountsApi } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';

interface CreateAccountParams {
  accountName: string;
  commissionRate: number;
  slippageRate: number;
}

interface DepositKrwParams {
  accountId: number;
  amount: number;
}

interface ExchangeCurrencyParams {
  accountId: number;
  fromCurrency: 'KRW' | 'USD';
  amount: number;
  exchangeRate: number;
}

interface DeleteAccountParams {
  accountId: number;
}

interface UseAccountOperationsReturn {
  createAccount: (params: CreateAccountParams) => Promise<void>;
  depositKrw: (params: DepositKrwParams) => Promise<void>;
  exchangeCurrency: (params: ExchangeCurrencyParams) => Promise<void>;
  deleteAccount: (params: DeleteAccountParams) => Promise<void>;
  loading: boolean;
}

// 계좌 생성 · 입금 · 환전 · 삭제. 성공하면 onSuccess 로 다시 불러옴
export const useAccountOperations = (
  onSuccess?: () => Promise<void>
): UseAccountOperationsReturn => {
  const [loading, setLoading] = useState(false);

  const createAccount = async ({ accountName, commissionRate, slippageRate }: CreateAccountParams) => {
    if (!accountName.trim()) {
      throw new Error('계좌명을 입력해주세요.');
    }

    if (isNaN(commissionRate) || commissionRate < 0 || commissionRate > 5) {
      throw new Error(`수수료율은 0 ~ 5% 사이여야 합니다.`);
    }

    if (isNaN(slippageRate) || slippageRate < 0 || slippageRate > 1) {
      throw new Error(`슬리피지율은 0 ~ 1% 사이여야 합니다.`);
    }

    try {
      setLoading(true);
      // 백엔드는 소수점 형식(0~0.05)을 기대하므로 100으로 나눔
      // 예: 0.25% → 0.0025, 1% → 0.01
      await accountsApi.createAccount({
        accountName,
        commissionRate: commissionRate / 100,
        slippageRate: slippageRate / 100
      });

      if (onSuccess) {
        await onSuccess();
      }

      alert('계좌가 생성되었습니다.');
    } catch (err: unknown) {
      alert('계좌 생성에 실패했습니다: ' + getApiErrorMessage(err, '알 수 없는 오류'));
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const depositKrw = async ({ accountId, amount }: DepositKrwParams) => {
    if (isNaN(amount) || amount <= 0) {
      throw new Error('올바른 금액을 입력해주세요.');
    }

    try {
      setLoading(true);
      await accountsApi.depositKrw(accountId, {
        krwAmount: amount
      });

      if (onSuccess) {
        await onSuccess();
      }

      alert('입금이 완료되었습니다.');
    } catch (err: unknown) {
      alert('입금에 실패했습니다: ' + getApiErrorMessage(err, '알 수 없는 오류'));
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const exchangeCurrency = async ({
    accountId,
    fromCurrency,
    amount,
    exchangeRate
  }: ExchangeCurrencyParams) => {
    if (isNaN(amount) || amount <= 0) {
      throw new Error('올바른 금액을 입력해주세요.');
    }

    if (isNaN(exchangeRate) || exchangeRate <= 0) {
      throw new Error('올바른 환율을 입력해주세요.');
    }

    try {
      setLoading(true);
      const toCurrency = fromCurrency === 'KRW' ? 'USD' : 'KRW';

      await accountsApi.exchangeCurrency(accountId, {
        fromCurrency,
        toCurrency,
        originalAmount: amount,
        exchangeRate
      });

      if (onSuccess) {
        await onSuccess();
      }

      alert('환전이 완료되었습니다.');
    } catch (err: unknown) {
      alert('환전에 실패했습니다: ' + getApiErrorMessage(err, '알 수 없는 오류'));
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const deleteAccount = async ({ accountId }: DeleteAccountParams) => {
    try {
      setLoading(true);

      await accountsApi.deleteAccount(accountId);

      if (onSuccess) {
        await onSuccess();
      }

      alert('계좌가 삭제되었습니다.');
    } catch (err: unknown) {
      alert('계좌 삭제에 실패했습니다: ' + getApiErrorMessage(err, '알 수 없는 오류'));
      throw err;
    } finally {
      setLoading(false);
    }
  };

  return {
    createAccount,
    depositKrw,
    exchangeCurrency,
    deleteAccount,
    loading
  };
};

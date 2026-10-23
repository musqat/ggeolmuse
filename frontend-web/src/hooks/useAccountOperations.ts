import { useState } from 'react';
import { accountsApi } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';
import { notify } from '@/components/common/notify';

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

// 계좌 생성 · 입금 · 환전 · 삭제(성공하면 onSuccess 로 다시 불러옴)
export const useAccountOperations = (
  onSuccess?: () => Promise<void>
): UseAccountOperationsReturn => {
  const [loading, setLoading] = useState(false);

  // 로딩 → API → 다시 불러오기 → 성공 알림, 실패면 '{label}에 실패했습니다: …' 알림 뒤 다시 던짐
  const run = async (label: string, successMessage: string, call: () => Promise<unknown>) => {
    try {
      setLoading(true);
      await call();

      if (onSuccess) {
        await onSuccess();
      }

      notify.success(successMessage);
    } catch (err: unknown) {
      notify.error(`${label}에 실패했습니다: ` + getApiErrorMessage(err, '알 수 없는 오류'));
      throw err;
    } finally {
      setLoading(false);
    }
  };

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

    // 서버는 비율을 소수로 받음(0.25% → 0.0025)
    await run('계좌 생성', '계좌가 생성되었습니다.', () =>
      accountsApi.createAccount({
        accountName,
        commissionRate: commissionRate / 100,
        slippageRate: slippageRate / 100
      })
    );
  };

  const depositKrw = async ({ accountId, amount }: DepositKrwParams) => {
    if (isNaN(amount) || amount <= 0) {
      throw new Error('올바른 금액을 입력해주세요.');
    }

    await run('입금', '입금이 완료되었습니다.', () =>
      accountsApi.depositKrw(accountId, { krwAmount: amount })
    );
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

    await run('환전', '환전이 완료되었습니다.', () =>
      accountsApi.exchangeCurrency(accountId, {
        fromCurrency,
        toCurrency: fromCurrency === 'KRW' ? 'USD' : 'KRW',
        originalAmount: amount,
        exchangeRate
      })
    );
  };

  const deleteAccount = ({ accountId }: DeleteAccountParams) =>
    run('계좌 삭제', '계좌가 삭제되었습니다.', () => accountsApi.deleteAccount(accountId));

  return {
    createAccount,
    depositKrw,
    exchangeCurrency,
    deleteAccount,
    loading
  };
};

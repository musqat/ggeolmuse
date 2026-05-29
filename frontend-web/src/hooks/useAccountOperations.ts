import { useState } from 'react';
import { accountsApi } from '../services/api';

export interface CreateAccountParams {
  accountName: string;
  commissionRate: number;
}

export interface DepositKrwParams {
  accountId: number;
  amount: number;
}

export interface ExchangeCurrencyParams {
  accountId: number;
  fromCurrency: 'KRW' | 'USD';
  amount: number;
  exchangeRate: number;
}

export interface DeleteAccountParams {
  accountId: number;
}

export interface UseAccountOperationsReturn {
  createAccount: (params: CreateAccountParams) => Promise<void>;
  depositKrw: (params: DepositKrwParams) => Promise<void>;
  exchangeCurrency: (params: ExchangeCurrencyParams) => Promise<void>;
  deleteAccount: (params: DeleteAccountParams) => Promise<void>;
  loading: boolean;
}

/**
 * 계좌 작업 훅
 *
 * @description 계좌 생성, 입금, 환전, 삭제 작업을 처리합니다
 * @param {() => Promise<void>} onSuccess - 작업 성공 시 실행할 콜백 (데이터 새로고침용)
 * @returns {UseAccountOperationsReturn} 계좌 작업 함수들과 로딩 상태
 */
export const useAccountOperations = (
  onSuccess?: () => Promise<void>
): UseAccountOperationsReturn => {
  const [loading, setLoading] = useState(false);

  /**
   * 계좌 생성
   *
   * @param {CreateAccountParams} params - 계좌명 및 수수료율
   * @throws {Error} 계좌명이 없거나 수수료율이 유효하지 않은 경우
   */
  const createAccount = async ({ accountName, commissionRate }: CreateAccountParams) => {
    if (!accountName.trim()) {
      throw new Error('계좌명을 입력해주세요.');
    }

    if (isNaN(commissionRate) || commissionRate < 0 || commissionRate > 5) {
      throw new Error(`수수료율은 0 ~ 5% 사이여야 합니다.`);
    }

    try {
      setLoading(true);
      // 백엔드는 소수점 형식(0~0.05)을 기대하므로 100으로 나눔
      // 예: 0.25% → 0.0025, 1% → 0.01
      await accountsApi.createAccount({
        accountName,
        commissionRate: commissionRate / 100
      });

      if (onSuccess) {
        await onSuccess();
      }

      alert('계좌가 생성되었습니다.');
    } catch (err: any) {
      alert('계좌 생성에 실패했습니다: ' + (err.response?.data?.detail || err.message));
      throw err;
    } finally {
      setLoading(false);
    }
  };

  /**
   * KRW 입금
   *
   * @param {DepositKrwParams} params - 계좌 ID 및 입금 금액
   * @throws {Error} 금액이 유효하지 않은 경우
   */
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
    } catch (err: any) {
      alert('입금에 실패했습니다: ' + (err.response?.data?.detail || err.message));
      throw err;
    } finally {
      setLoading(false);
    }
  };

  /**
   * 환전 처리
   *
   * @param {ExchangeCurrencyParams} params - 계좌 ID, 출발 통화, 금액, 환율
   * @throws {Error} 금액이나 환율이 유효하지 않은 경우
   */
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
    } catch (err: any) {
      alert('환전에 실패했습니다: ' + (err.response?.data?.detail || err.message));
      throw err;
    } finally {
      setLoading(false);
    }
  };

  /**
   * 계좌 삭제
   *
   * @param {DeleteAccountParams} params - 계좌 ID
   * @throws {Error} 삭제 실패 시
   */
  const deleteAccount = async ({ accountId }: DeleteAccountParams) => {
    try {
      setLoading(true);

      await accountsApi.deleteAccount(accountId);

      if (onSuccess) {
        await onSuccess();
      }

      alert('계좌가 삭제되었습니다.');
    } catch (err: any) {
      alert('계좌 삭제에 실패했습니다: ' + (err.response?.data?.detail || err.message));
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

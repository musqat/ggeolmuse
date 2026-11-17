import React from 'react';
import { Link } from 'react-router-dom';
import type { Account } from '@/hooks/useAccounts';

interface FirstOrderGuideProps {
  accounts: Account[];
  selectedAccountId: number | null;
  orderType: 'buy' | 'sell';
}

const LINK_CLASS = 'mt-2 inline-block text-sm font-medium text-brand underline underline-offset-2';

// 첫 주문까지 할 일(계좌가 없을 때)과 달러가 없는 계좌로 매수할 때의 입금 · 환전 바로 가기
const FirstOrderGuide: React.FC<FirstOrderGuideProps> = ({ accounts, selectedAccountId, orderType }) => {
  if (accounts.length === 0) {
    return (
      <div role="note" className="mb-4 rounded-lg bg-brand-bg p-3 text-sm">
        <p className="font-medium text-tx-1">첫 주문까지</p>
        <ol className="mt-1 list-decimal pl-5 text-tx-2">
          <li>계좌 만들기</li>
          <li>원화 입금</li>
          <li>달러로 환전</li>
        </ol>
        <Link to="/account" className={LINK_CLASS}>계좌 만들러 가기</Link>
      </div>
    );
  }

  const selected = accounts.find((account) => account.accountId === selectedAccountId);
  const noDollars = orderType === 'buy' && typeof selected?.usdBalance === 'number' && selected.usdBalance <= 0;
  if (!noDollars) return null;

  return (
    <div role="note" className="mb-4 rounded-lg bg-brand-bg p-3 text-sm">
      <p className="text-tx-1">달러가 없어 매수할 수 없습니다. 원화를 입금하고 달러로 환전하세요.</p>
      <Link to="/account" className={LINK_CLASS}>입금 · 환전하러 가기</Link>
    </div>
  );
};

export default FirstOrderGuide;

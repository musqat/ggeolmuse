import React, { useState } from 'react';
import { Modal } from '@/components/common/Modal';

interface CreateAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (accountName: string, commissionRate: number, slippageRate: number) => Promise<void>;
}

// 비율 칸에 음수가 들어가지 않게 '-' 입력과 '-' 가 든 붙여넣기를 막는다
const blockMinus = {
  onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === '-') e.preventDefault();
  },
  onPaste: (e: React.ClipboardEvent<HTMLInputElement>) => {
    if (e.clipboardData.getData('text').includes('-')) e.preventDefault();
  },
};

/**
 * 계좌 생성 모달 컴포넌트
 * 계좌명, 거래 수수료율, 슬리피지율을 입력받아 새 계좌를 생성합니다.
 */

const CreateAccountModal: React.FC<CreateAccountModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [accountName, setAccountName] = useState('');
  const [commissionRate, setCommissionRate] = useState('0.25');
  const [slippageRate, setSlippageRate] = useState('0.1');

  const handleClose = () => {
    setAccountName('');
    setCommissionRate('0.25');
    setSlippageRate('0.1');
    onClose();
  };

  const handleSubmit = async () => {
    // 폼 검증
    if (!accountName.trim()) {
      alert('계좌명을 입력해주세요.');
      return;
    }

    // 수수료율 검증 - 빈 문자열 체크 추가
    if (!commissionRate || commissionRate.trim() === '') {
      alert('수수료율을 입력해주세요.');
      return;
    }

    const commissionRatePercent = parseFloat(commissionRate);
    if (isNaN(commissionRatePercent)) {
      alert('수수료율은 숫자로 입력해주세요.');
      return;
    }

    if (commissionRatePercent < 0 || commissionRatePercent > 5) {
      alert(`수수료율은 0 ~ 5% 사이여야 합니다. (입력값: ${commissionRatePercent}%)`);
      return;
    }

    // 슬리피지율 검증
    if (!slippageRate || slippageRate.trim() === '') {
      alert('슬리피지율을 입력해주세요.');
      return;
    }

    const slippageRatePercent = parseFloat(slippageRate);
    if (isNaN(slippageRatePercent)) {
      alert('슬리피지율은 숫자로 입력해주세요.');
      return;
    }

    if (slippageRatePercent < 0 || slippageRatePercent > 1) {
      alert(`슬리피지율은 0 ~ 1% 사이여야 합니다. (입력값: ${slippageRatePercent}%)`);
      return;
    }

    // 제출
    await onSubmit(accountName, commissionRatePercent, slippageRatePercent);
    handleClose();
  };

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
        disabled={!accountName.trim()}
        className="flex-1 px-4 py-2 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        생성하기
      </button>
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="새 계좌 생성"
      footer={footer}
      maxWidth="md"
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">계좌명</label>
          <input
            type="text"
            value={accountName}
            onChange={(e) => setAccountName(e.target.value)}
            placeholder="예: 주식 투자 계좌"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
        </div>

        <div>
          <label htmlFor="commission-rate" className="block text-sm font-medium text-tx-1 mb-2">
            거래 수수료 (%)
          </label>
          <input
            id="commission-rate"
            type="number"
            step="0.01"
            min="0"
            max="5"
            value={commissionRate}
            onChange={(e) => setCommissionRate(e.target.value)}
            {...blockMinus}
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <p className="text-xs text-tx-2 mt-1">0 ~ 5% 사이의 값을 입력하세요</p>
        </div>

        <div>
          <label htmlFor="slippage-rate" className="block text-sm font-medium text-tx-1 mb-2">
            슬리피지 (%)
          </label>
          <input
            id="slippage-rate"
            type="number"
            step="0.01"
            min="0"
            max="1"
            value={slippageRate}
            onChange={(e) => setSlippageRate(e.target.value)}
            {...blockMinus}
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <p className="text-xs text-tx-2 mt-1">0 ~ 1%. 매수는 이만큼 비싸게, 매도는 싸게 체결됩니다</p>
        </div>
      </div>
    </Modal>
  );
};

export default CreateAccountModal;

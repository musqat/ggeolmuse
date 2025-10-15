import React, { useState } from 'react';
import { Modal } from '@/components/common/Modal';

interface DepositModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (amount: number) => Promise<void>;
  accountId: number | null;
}

/**
 * 입금 모달 컴포넌트
 * KRW 금액을 입력받아 계좌에 입금합니다.
 */
export const DepositModal: React.FC<DepositModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  accountId,
}) => {
  const [depositAmount, setDepositAmount] = useState('');

  // 빠른 금액 버튼 옵션
  const quickAmounts = [100000, 1000000, 10000000];

  const handleClose = () => {
    setDepositAmount('');
    onClose();
  };

  const handleSubmit = async () => {
    const amount = parseFloat(depositAmount);
    if (isNaN(amount) || amount <= 0) {
      alert('올바른 금액을 입력해주세요.');
      return;
    }

    await onSubmit(amount);
    handleClose();
  };

  const footer = (
    <div className="flex space-x-3">
      <button
        onClick={handleClose}
        className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
      >
        취소
      </button>
      <button
        onClick={handleSubmit}
        disabled={!depositAmount || parseFloat(depositAmount) <= 0}
        className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
      >
        입금하기
      </button>
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="KRW 입금"
      footer={footer}
      maxWidth="md"
    >
      <div className="mb-4 p-3 bg-blue-50 rounded-lg">
        <p className="text-sm text-blue-800">
          가상 투자 자금을 추가합니다.
        </p>
      </div>

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">입금 금액 (KRW)</label>
          <input
            type="number"
            value={depositAmount}
            onChange={(e) => setDepositAmount(e.target.value)}
            placeholder="입금할 금액을 입력하세요"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <div className="grid grid-cols-3 gap-2">
          {quickAmounts.map((amount) => (
            <button
              key={amount}
              onClick={() => setDepositAmount(amount.toString())}
              className="py-2 px-3 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200 transition-colors text-sm"
            >
              ₩{(amount / 10000).toFixed(0)}만
            </button>
          ))}
        </div>
      </div>
    </Modal>
  );
};

export default DepositModal;

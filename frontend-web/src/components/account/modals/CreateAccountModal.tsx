import React, { useState } from 'react';
import { Modal } from '@/components/common/Modal';

interface CreateAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (accountName: string, commissionRate: number) => Promise<void>;
}

/**
 * 계좌 생성 모달 컴포넌트
 * 계좌명과 거래 수수료율을 입력받아 새 계좌를 생성합니다.
 */
export const CreateAccountModal: React.FC<CreateAccountModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [accountName, setAccountName] = useState('');
  const [commissionRate, setCommissionRate] = useState('0.25');

  const handleClose = () => {
    setAccountName('');
    setCommissionRate('0.25');
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

    // 제출
    await onSubmit(accountName, commissionRatePercent);
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
        disabled={!accountName.trim()}
        className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
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
          <label className="block text-sm font-medium text-gray-700 mb-2">계좌명</label>
          <input
            type="text"
            value={accountName}
            onChange={(e) => setAccountName(e.target.value)}
            placeholder="예: 주식 투자 계좌"
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            거래 수수료 (%)
          </label>
          <input
            type="number"
            step="0.01"
            min="0"
            max="5"
            value={commissionRate}
            onChange={(e) => setCommissionRate(e.target.value)}
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p className="text-xs text-gray-500 mt-1">0 ~ 5% 사이의 값을 입력하세요</p>
        </div>
      </div>
    </Modal>
  );
};

export default CreateAccountModal;

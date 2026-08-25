import React from "react";
import { Modal } from "@/components/common/Modal";
import { Trash2 } from "lucide-react";

interface DeleteAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  accountId: number | null;
}

const DeleteAccountModal: React.FC<DeleteAccountModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  accountId,
}) => {
  const handleConfirm = async () => {
    await onConfirm();
    onClose();
  };

  const footer = (
    <div className="flex space-x-3">
      <button
        onClick={onClose}
        className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
      >
        취소
      </button>
      <button
        onClick={handleConfirm}
        className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
      >
        삭제하기
      </button>
    </div>
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div className="flex items-center">
          <Trash2 className="w-6 h-6 mr-2 text-red-600" />
          계좌 삭제 확인
        </div>
      }
      footer={footer}
      maxWidth="md"
    >
      <div>
        <p className="text-tx-1 mb-2">정말로 이 계좌를 삭제하시겠습니까?</p>
        <div className="bg-yellow-500/10 border border-yellow-500/25 rounded-lg p-3 mt-3">
          <p className="text-sm text-yellow-600 font-medium">주의사항</p>
          <ul className="text-sm text-yellow-600 mt-2 space-y-1 list-disc list-inside">
            <li>삭제된 계좌는 복구할 수 없습니다</li>
            <li>거래 내역도 함께 삭제됩니다</li>
            <li>잔액이 있는 경우 삭제 시 잔액이 소멸됩니다</li>
          </ul>
        </div>
      </div>
    </Modal>
  );
};

export default DeleteAccountModal;

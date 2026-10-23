import { useRef, useState } from 'react';
import { notify } from '@/components/common/notify';

interface AdminAction {
  // 먼저 물어볼 문구, 취소하면 아무것도 안 함
  confirm?: { text: string; danger?: boolean };
  // API · 알림 · 다시 받기
  call: () => Promise<void>;
  // 실패 문구(고정 문구 또는 서버 문구)
  failure: (err: unknown) => string;
}

// 관리자 화면 작업 실행 틀(확인 → 로딩 · 오류 비움 → 작업 → 실패 문구 → 로딩 끔), 끝까지 성공하면 true
export const useAdminAction = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 진행 중인 작업 수(작업 안에서 부른 다시 불러오기가 끝날 때까지 로딩 유지)
  const running = useRef(0);

  const run = async ({ confirm, call, failure }: AdminAction): Promise<boolean> => {
    if (confirm) {
      const ok = confirm.danger
        ? await notify.confirm(confirm.text, { danger: true })
        : await notify.confirm(confirm.text);
      if (!ok) return false;
    }

    running.current += 1;
    setLoading(true);
    setError(null);
    try {
      await call();
      return true;
    } catch (err) {
      const message = failure(err);
      setError(message);
      console.error(message, err);
      return false;
    } finally {
      running.current -= 1;
      if (running.current === 0) setLoading(false);
    }
  };

  return { loading, error, setError, run };
};

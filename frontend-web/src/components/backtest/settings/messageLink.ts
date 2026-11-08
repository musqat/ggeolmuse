import { useId } from 'react';
import { isError, type FieldMessage } from './checks';

// 칸 아래 한 줄의 id 와 입력에 넘길 aria-invalid · aria-describedby 값
export const useMessageLink = (message?: FieldMessage | null) => {
  const messageId = useId();
  return { messageId, invalid: isError(message), describedBy: message ? messageId : undefined };
};

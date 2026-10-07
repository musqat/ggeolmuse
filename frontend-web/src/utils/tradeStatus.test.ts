import { describe, it, expect } from 'vitest'
import { cancelReasonLabel } from './tradeStatus'

describe('cancelReasonLabel', () => {
  it('아는 사유 코드는 화면 문구로 바꾼다', () => {
    expect(cancelReasonLabel('INSUFFICIENT_USD_BALANCE')).toBe('잔액 부족')
    expect(cancelReasonLabel('ACCOUNT_NOT_FOUND')).toBe('계좌 없음')
    expect(cancelReasonLabel('ACCOUNT_ACCESS_DENIED')).toBe('계좌 권한 없음')
  })

  it('모르는 코드나 빈 값은 잔액 반영 실패', () => {
    expect(cancelReasonLabel('INVALID_TRANSACTION_TYPE')).toBe('잔액 반영 실패')
    expect(cancelReasonLabel(undefined)).toBe('잔액 반영 실패')
    expect(cancelReasonLabel(null)).toBe('잔액 반영 실패')
  })
})

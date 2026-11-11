import { describe, it, expect } from 'vitest'
import { hasPartialTakeProfits, savedSteps, toPartialTakeProfits } from './partialTakeProfit'

const step = (gain: string, sell: string) => ({ gain, sell })

describe('toPartialTakeProfits', () => {
  it('단계가 없으면 칸을 싣지 않는다', () => {
    expect(toPartialTakeProfits([])).toStrictEqual({ fields: {} })
  })

  it('단계마다 수익률 · 팔 퍼센트를 소수 비율로 바꾸고 나눗셈 끝자리 오차를 버린다', () => {
    expect(toPartialTakeProfits([step(' 20 ', '25'), step('33.3', '100')])).toStrictEqual({
      fields: {
        partialTakeProfits: [
          { gainRate: 0.2, sellRate: 0.25 },
          { gainRate: 0.333, sellRate: 1 },
        ],
      },
    })
  })

  it('5단계까지 싣는다', () => {
    const five = [step('10', '10'), step('20', '10'), step('30', '10'), step('40', '10'), step('50', '100')]
    const built = toPartialTakeProfits(five)

    expect('fields' in built && built.fields.partialTakeProfits).toHaveLength(5)
  })

  it.each([
    [[step('', '25')],'부분 익절 1단계 수익률은 0보다 크고 앞 단계보다 커야 합니다.'],
    [[step('0', '25')], '부분 익절 1단계 수익률은 0보다 크고 앞 단계보다 커야 합니다.'],
    [[step('.', '25')], '부분 익절 1단계 수익률은 0보다 크고 앞 단계보다 커야 합니다.'],
    [[step('20', '25'), step('20', '25')], '부분 익절 2단계 수익률은 0보다 크고 앞 단계보다 커야 합니다.'],
    [[step('20', '25'), step('10', '25')], '부분 익절 2단계 수익률은 0보다 크고 앞 단계보다 커야 합니다.'],
    [[step('20', '')], '부분 익절 1단계 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [[step('20', '0')], '부분 익절 1단계 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [[step('10', '25'), step('20', '100.5')], '부분 익절 2단계 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [
      [step('10', '1'), step('20', '1'), step('30', '1'), step('40', '1'), step('50', '1'), step('60', '1')],
      '부분 익절은 5단계까지 넣을 수 있습니다.',
    ],
  ])('%o 는 막는다', (steps, error) => {
    expect(toPartialTakeProfits(steps)).toStrictEqual({ error })
  })
})

describe('hasPartialTakeProfits', () => {
  it('단계를 하나라도 두었을 때만 켠 것으로 본다', () => {
    expect(hasPartialTakeProfits([step('', '')])).toBe(true)
    expect(hasPartialTakeProfits([])).toBe(false)
    expect(hasPartialTakeProfits(undefined)).toBe(false)
  })
})

describe('savedSteps', () => {
  it('글 칸 둘인 단계만 5단계까지 되살리고 다른 칸은 버린다', () => {
    const saved = {
      partialTakeProfits: [step('10', '20'), { gain: 30, sell: '40' }, null, { gain: '50', sell: '60', extra: 1 },
        step('1', '1'), step('2', '2'), step('3', '3'), step('4', '4')],
    }

    expect(savedSteps(saved)).toStrictEqual([step('10', '20'), step('50', '60'), step('1', '1'), step('2', '2'), step('3', '3')])
    expect(savedSteps({ partialTakeProfits: '10' })).toStrictEqual([])
    expect(savedSteps(undefined)).toStrictEqual([])
  })
})

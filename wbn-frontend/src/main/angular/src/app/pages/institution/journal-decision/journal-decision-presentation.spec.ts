import {
  formatDecisionNumber,
  formatDecisionPercentage,
  formatDecisionTimeRange,
} from './journal-decision-presentation';

describe('Decision presentation rules', () => {
  it.each([
    [0, '0%'],
    [0.00001, '<1%'],
    [0.3, '<1%'],
    [0.4999, '<1%'],
    [0.5, '1%'],
    [78.4, '78%'],
    [78.5, '79%'],
    [99.49, '99%'],
    [99.5, '>99%'],
    [99.8, '>99%'],
    [100, '100%'],
  ])('formats comparison percentage %s as %s in both languages', (value, expected) => {
    for (const language of ['pl', 'en'])
      expect(formatDecisionPercentage(value, language)).toBe(expected);
  });
  it.each([null, undefined, NaN, Infinity, -1, 100.01])(
    'does not turn invalid percentage %s into zero',
    (value) => {
      expect(formatDecisionPercentage(value, 'pl')).toBe('—');
    },
  );
  it('keeps ICV precision separate from comparison percentages and formats bounds outwards', () => {
    expect(formatDecisionNumber(81.125, 'pl')).toBe('81,125');
    expect(formatDecisionNumber(81.125, 'en')).toBe('81.125');
    expect(formatDecisionTimeRange(18.25, 32.75, 'pl')).toBe('18–33');
    expect(formatDecisionTimeRange(12.1, 12.2, 'en')).toBe('12–13');
    expect(formatDecisionTimeRange(12.5, 12.5, 'pl')).toBe('13');
    expect(formatDecisionTimeRange(0, 0, 'en')).toBe('0');
  });
});

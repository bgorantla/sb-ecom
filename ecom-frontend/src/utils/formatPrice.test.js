import { describe, expect, it } from 'vitest';
import { formatPrice, formatRevenue } from './formatPrice';

describe('formatPrice', () => {
    it('shows dollars with cents and thousands separators', () => {
        expect(formatPrice(1234.5)).toBe('$1,234.50');
        expect(formatPrice(0)).toBe('$0.00');
    });
});

describe('formatRevenue', () => {
    it('shortens big numbers for the dashboard', () => {
        expect(formatRevenue(950)).toBe(950);
        expect(formatRevenue(1500)).toBe('1.5K');
        expect(formatRevenue(2_500_000)).toBe('2.5M');
        expect(formatRevenue(3_000_000_000)).toBe('3.0B');
    });
});

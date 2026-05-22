import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Sparkline from '../Sparkline';

describe('Sparkline', () => {
  it('renders empty when values are empty', () => {
    const { container } = render(<Sparkline values={[]} />);
    expect(container.querySelector('svg')).toBeNull();
  });

  it('renders polyline with correct point count', () => {
    const { container } = render(<Sparkline values={[1, 2, 3, 4]} />);
    const polyline = container.querySelector('polyline');
    expect(polyline).not.toBeNull();
    const points = polyline!.getAttribute('points')!.trim().split(/\s+/);
    expect(points).toHaveLength(4);
  });

  it('normalizes values so max value is at top', () => {
    const { container } = render(<Sparkline values={[0, 10]} />);
    const points = container
      .querySelector('polyline')!
      .getAttribute('points')!
      .trim()
      .split(/\s+/);
    // 값 10에 해당하는 점의 y좌표는 0 (top), 값 0의 y좌표는 24 (bottom)
    const ys = points.map((p) => parseFloat(p.split(',')[1]));
    expect(ys[0]).toBeCloseTo(24, 1);
    expect(ys[1]).toBeCloseTo(0, 1);
  });

  it('renders <title> with provided label and exposes svg as role="img"', () => {
    const { container } = render(<Sparkline values={[1, 2, 3]} label="최근 7일 추세" />);
    const svg = container.querySelector('svg')!;
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.hasAttribute('aria-hidden')).toBe(false);
    const title = svg.querySelector('title');
    expect(title).not.toBeNull();
    expect(title!.textContent).toBe('최근 7일 추세');
  });
});

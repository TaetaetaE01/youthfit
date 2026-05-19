import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { SubRegionInline } from '../SubRegionInline';
import type { PolicySubRegion } from '@/types/policy';

const sr = (name: string, sidoName = '서울'): PolicySubRegion => ({
  code: name, sidoCode: 'S', sidoName, name,
});

describe('SubRegionInline', () => {
  it('비어있으면 null', () => {
    const { container } = render(<SubRegionInline subRegions={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('5개 이하면 전체를 점으로 연결', () => {
    render(<SubRegionInline subRegions={[sr('관악구'), sr('동작구'), sr('강서구')]} />);
    expect(screen.getByText(/관악구·동작구·강서구/)).toBeInTheDocument();
  });

  it('6개 이상이면 첫 1개 + 외 N개', () => {
    const list = ['관악구', '동작구', '강서구', '강남구', '서초구', '송파구'].map((n) => sr(n));
    render(<SubRegionInline subRegions={list} />);
    expect(screen.getByText(/관악구 외 5개/)).toBeInTheDocument();
  });
});

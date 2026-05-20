import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import RegionPickerBanner from './RegionPickerBanner';

describe('RegionPickerBanner', () => {
  it('지역 라벨을 본문에 노출한다', () => {
    render(<RegionPickerBanner regionLabel="서울 강남구" onDismiss={() => {}} />);
    expect(screen.getByText(/서울 강남구/)).toBeInTheDocument();
  });

  it('role="status" 를 가진다', () => {
    render(<RegionPickerBanner regionLabel="서울" onDismiss={() => {}} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('"해제" 버튼 클릭 시 onDismiss 가 호출된다', () => {
    const onDismiss = vi.fn();
    render(<RegionPickerBanner regionLabel="서울" onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole('button', { name: /해제/ }));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});

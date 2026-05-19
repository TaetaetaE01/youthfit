import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { PolicyMobileBottomBar } from '../PolicyMobileBottomBar';

describe('PolicyMobileBottomBar', () => {
  it('applyUrl이 있으면 신청 버튼이 렌더된다', () => {
    render(
      <PolicyMobileBottomBar
        applyUrl="https://example.com/apply"
        isSubscribed={false}
        visible
        onNotificationClick={() => {}}
      />,
    );
    expect(screen.getByRole('link', { name: /공식 신청 페이지/ })).toBeInTheDocument();
  });

  it('applyUrl 도 sourceUrl 도 없으면 신청 버튼 자체가 없다', () => {
    render(
      <PolicyMobileBottomBar
        applyUrl={null}
        sourceUrl={null}
        isSubscribed={false}
        visible
        onNotificationClick={() => {}}
      />,
    );
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('알림 토글 버튼을 누르면 콜백이 호출된다', () => {
    const onClick = vi.fn();
    render(
      <PolicyMobileBottomBar
        applyUrl="https://example.com"
        isSubscribed={false}
        visible
        onNotificationClick={onClick}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /알림/ }));
    expect(onClick).toHaveBeenCalled();
  });

  it('visible=false면 translate-y-full 적용', () => {
    const { container } = render(
      <PolicyMobileBottomBar
        applyUrl="https://example.com"
        isSubscribed={false}
        visible={false}
        onNotificationClick={() => {}}
      />,
    );
    expect(container.firstChild).toHaveClass('translate-y-full');
  });
});

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { SourceType } from '@/types/policy';
import type { SourceTag } from '@/types/adminPolicyProcessing';
import { SourceBadges } from '../SourceBadges';

describe('SourceBadges', () => {
  it('출처가 여러 개면 전부 한글 라벨로 렌더한다', () => {
    render(
      <SourceBadges
        sources={[
          { code: 'YOUTH_SEOUL_CRAWL', label: '청년몽땅정보통' },
          { code: 'BOKJIRO_CENTRAL', label: '복지로' },
        ]}
      />,
    );
    expect(screen.getByText('청년몽땅정보통')).toBeInTheDocument();
    expect(screen.getByText('복지로')).toBeInTheDocument();
  });

  it('출처가 없으면 출처없음 뱃지를 렌더한다', () => {
    render(<SourceBadges sources={[]} />);
    expect(screen.getByText('출처없음')).toBeInTheDocument();
  });

  it('sources 가 undefined(구버전 백엔드 응답)여도 크래시 없이 출처없음을 렌더한다', () => {
    // 백엔드가 아직 sources 필드를 내려주지 않는 경우를 모사. 렌더가 throw 하면 안 된다.
    render(<SourceBadges sources={undefined as unknown as SourceTag[]} />);
    expect(screen.getByText('출처없음')).toBeInTheDocument();
  });

  it('타입에 없는 코드(런타임)에도 라벨을 그대로 중립 색으로 렌더한다', () => {
    // 백엔드가 SourceType 에 새 출처를 추가했지만 프런트 타입이 아직 안 좁혀진 런타임 상황을 모사.
    render(<SourceBadges sources={[{ code: 'UNKNOWN' as SourceType, label: '기타' }]} />);
    expect(screen.getByText('기타')).toBeInTheDocument();
  });
});

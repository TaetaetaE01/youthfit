import { describe, test, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { EnrichmentReferenceSiteEditor } from '../EnrichmentReferenceSiteEditor';

describe('EnrichmentReferenceSiteEditor', () => {
  test('https 가 아닌 URL 입력 시 인라인 에러', () => {
    render(<EnrichmentReferenceSiteEditor initialSites={[]} onSave={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText(/URL/i), { target: { value: 'not-a-url' } });
    fireEvent.click(screen.getByRole('button', { name: /추가/ }));
    expect(screen.getByText(/올바른 URL/i)).toBeInTheDocument();
  });

  test('동일 URL 중복 입력 시 인라인 에러', () => {
    render(
      <EnrichmentReferenceSiteEditor
        initialSites={[{ name: 'a', url: 'https://a.example.com', source: 'AUTO' }]}
        onSave={() => {}}
      />,
    );
    fireEvent.change(screen.getByPlaceholderText(/URL/i), {
      target: { value: 'https://a.example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: /추가/ }));
    expect(screen.getByText(/이미 등록/i)).toBeInTheDocument();
  });

  test('저장 클릭 시 onSave가 ADMIN 플래그로 호출된다', () => {
    const onSave = vi.fn();
    render(<EnrichmentReferenceSiteEditor initialSites={[]} onSave={onSave} />);
    fireEvent.change(screen.getByPlaceholderText(/이름/i), {
      target: { value: '확인된 사이트' },
    });
    fireEvent.change(screen.getByPlaceholderText(/URL/i), {
      target: { value: 'https://verified.example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: /추가/ }));
    fireEvent.click(screen.getByRole('button', { name: /저장/ }));
    expect(onSave).toHaveBeenCalledWith([
      { name: '확인된 사이트', url: 'https://verified.example.com', source: 'ADMIN' },
    ]);
  });
});

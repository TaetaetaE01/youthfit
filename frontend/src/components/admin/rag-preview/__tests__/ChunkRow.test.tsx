import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ChunkRow } from '../ChunkRow';

describe('ChunkRow', () => {
  it('truncated preview 표시 + 클릭 시 expand', () => {
    const long = 'a'.repeat(200);
    render(<ChunkRow rank={1} chunkId={7} distance={0.21} preview={long} />);
    expect(screen.getByText(/a/).textContent?.length).toBeLessThan(200);
    fireEvent.click(screen.getByText(/a/));
    expect(screen.getByText(/a/).textContent?.length).toBe(200);
  });

  it('distance 와 rrfScore 표시', () => {
    render(<ChunkRow rank={2} chunkId={9} distance={0.5} rrfScore={0.0321} preview="x" />);
    expect(screen.getByText('d=0.500')).toBeInTheDocument();
    expect(screen.getByText('rrf=0.0321')).toBeInTheDocument();
  });

  it('카드 컨테이너에 rounded-md 클래스가 적용된다', () => {
    const { container } = render(<ChunkRow rank={1} chunkId={1} preview="hello" />);
    const card = container.firstChild as HTMLElement;
    expect(card.className).toMatch(/rounded-md/);
  });
});

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CandidateConfigForm } from '../CandidateConfigForm';

const baseline = {
  hybridEnabled: true, topNPerSearch: 20, rrfK: 60,
  trigramThreshold: 0.1, keywordBoostEnabled: true, maxKeywords: 5,
};

describe('CandidateConfigForm', () => {
  it('baseline 으로 prefill 된다', () => {
    render(<CandidateConfigForm baseline={baseline} onChange={() => {}} />);
    expect(screen.getByDisplayValue('20')).toBeInTheDocument();   // topNPerSearch
    expect(screen.getByDisplayValue('60')).toBeInTheDocument();   // rrfK
  });

  it('rrfK 변경 시 onChange 가 그 필드만 포함해 호출된다', async () => {
    const onChange = vi.fn();
    render(<CandidateConfigForm baseline={baseline} onChange={onChange} />);
    const rrfK = screen.getByDisplayValue('60') as HTMLInputElement;
    fireEvent.change(rrfK, { target: { value: '30' } });

    await waitFor(() => {
      const last = onChange.mock.calls.at(-1)?.[0];
      expect(last).toEqual({ rrfK: 30 });
    });
  });
});

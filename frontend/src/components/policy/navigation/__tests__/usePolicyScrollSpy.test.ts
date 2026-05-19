import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { usePolicyScrollSpy } from '../usePolicyScrollSpy';

class MockIntersectionObserver {
  static instances: MockIntersectionObserver[] = [];
  callback: IntersectionObserverCallback;
  observed: Element[] = [];
  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback;
    MockIntersectionObserver.instances.push(this);
  }
  observe(el: Element) { this.observed.push(el); }
  unobserve() {}
  disconnect() {}
  trigger(entries: Partial<IntersectionObserverEntry>[]) {
    this.callback(entries as IntersectionObserverEntry[], this as unknown as IntersectionObserver);
  }
}

describe('usePolicyScrollSpy', () => {
  beforeEach(() => {
    MockIntersectionObserver.instances = [];
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);
    document.body.innerHTML = `
      <section id="eligibility"></section>
      <section id="benefits"></section>
      <section id="apply"></section>
      <section id="more"></section>
    `;
  });

  it('초기 active는 첫 그룹', () => {
    const { result } = renderHook(() => usePolicyScrollSpy(['eligibility', 'benefits', 'apply', 'more']));
    expect(result.current.activeId).toBe('eligibility');
  });

  it('intersection 변경 시 active 변경', () => {
    const { result } = renderHook(() => usePolicyScrollSpy(['eligibility', 'benefits', 'apply', 'more']));
    const observer = MockIntersectionObserver.instances[0];
    act(() => {
      observer.trigger([
        { target: document.getElementById('benefits')!, isIntersecting: true, intersectionRatio: 0.5 },
      ]);
    });
    expect(result.current.activeId).toBe('benefits');
  });
});

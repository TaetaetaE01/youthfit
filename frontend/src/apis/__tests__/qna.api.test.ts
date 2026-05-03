import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fetchQnaAnswer } from '../qna.api';

function makeSseStream(events: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      events.forEach((line) => controller.enqueue(encoder.encode(line + '\n')));
      controller.close();
    },
  });
}

describe('fetchQnaAnswer', () => {
  beforeEach(() => {
    vi.spyOn(globalThis, 'fetch');
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('CHUNK / SOURCES / DONE 이벤트를 파싱해 콜백을 호출한다', async () => {
    const stream = makeSseStream([
      'data: {"type":"CHUNK","content":"안녕"}',
      'data: {"type":"CHUNK","content":"하세요"}',
      'data: {"type":"SOURCES","sources":[{"policyId":1,"attachmentLabel":"청년정책 시행계획","pageStart":12,"pageEnd":13}]}',
      'data: {"type":"DONE"}',
    ]);

    (globalThis.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce(
      new Response(stream, { status: 200 }),
    );

    const onChunk = vi.fn();
    const onSources = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await fetchQnaAnswer(
      1,
      '신청 자격은?',
      { onChunk, onSources, onDone, onError },
      'token-abc',
    );

    expect(onChunk).toHaveBeenNthCalledWith(1, '안녕');
    expect(onChunk).toHaveBeenNthCalledWith(2, '하세요');
    expect(onSources).toHaveBeenCalledWith(['청년정책 시행계획 p.12-13']);
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
  });

  it('AbortSignal 로 취소되면 silent 하게 종료한다', async () => {
    const controller = new AbortController();
    (globalThis.fetch as unknown as ReturnType<typeof vi.fn>).mockImplementationOnce(
      (_url, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => {
            reject(new DOMException('aborted', 'AbortError'));
          });
        }),
    );

    const onError = vi.fn();
    const onDone = vi.fn();

    const p = fetchQnaAnswer(
      1,
      'q',
      { onChunk: vi.fn(), onSources: vi.fn(), onDone, onError },
      'token',
      controller.signal,
    );
    controller.abort();
    await p;

    expect(onError).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
  });

  it('인증 토큰이 없으면 onError 호출하고 fetch 안 함', async () => {
    const onError = vi.fn();
    await fetchQnaAnswer(
      1,
      'q',
      { onChunk: vi.fn(), onSources: vi.fn(), onDone: vi.fn(), onError },
      null,
    );
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: '인증이 필요합니다' }));
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});

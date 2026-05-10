import type { QnaSource } from '@/types/qna';

const QNA_URL = '/api/v1/qna/ask';

export interface QnaCallbacks {
  onChunk: (text: string) => void;
  onSources: (sources: QnaSource[]) => void;
  onSuggestions: (questions: string[]) => void;
  onDone: () => void;
  onError: (error: Error) => void;
}

export async function fetchQnaAnswer(
  policyId: number,
  question: string,
  callbacks: QnaCallbacks,
  accessToken: string | null,
  signal?: AbortSignal,
): Promise<void> {
  const { onChunk, onSources, onSuggestions, onDone, onError } = callbacks;

  if (!accessToken) {
    onError(new Error('인증이 필요합니다'));
    return;
  }

  let response: Response;
  try {
    response = await fetch(QNA_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ policyId, question }),
      signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return;
    }
    onError(e instanceof Error ? e : new Error('네트워크 오류'));
    return;
  }

  if (!response.ok) {
    onError(new Error(`Q&A 요청 실패: ${response.status}`));
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    onError(new Error('스트림을 읽을 수 없습니다'));
    return;
  }

  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (!data) continue;

        try {
          const parsed = JSON.parse(data);
          if (parsed.type === 'CHUNK') {
            onChunk(parsed.content ?? '');
          } else if (parsed.type === 'SOURCES') {
            const sources: QnaSource[] = (parsed.sources ?? []).map(
              (s: Partial<QnaSource>) => ({
                policyId: s.policyId ?? 0,
                attachmentLabel: s.attachmentLabel ?? null,
                pageStart: s.pageStart ?? null,
                pageEnd: s.pageEnd ?? null,
                excerpt: s.excerpt ?? null,
              }),
            );
            onSources(sources);
          } else if (parsed.type === 'SUGGESTIONS') {
            onSuggestions(parsed.questions ?? []);
          } else if (parsed.type === 'DONE') {
            onDone();
            return;
          } else if (parsed.type === 'ERROR') {
            onError(new Error(parsed.content ?? '답변 생성 중 오류가 발생했습니다'));
            return;
          }
        } catch {
          // SSE 데이터가 partial JSON 인 경우는 발생하지 않으나 안전하게 무시
        }
      }
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return;
    }
    onError(e instanceof Error ? e : new Error('스트림 읽기 오류'));
    return;
  }

  onDone();
}

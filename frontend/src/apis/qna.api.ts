const QNA_URL = '/api/v1/qna/ask';

export async function fetchQnaAnswer(
  policyId: number,
  question: string,
  onChunk: (text: string) => void,
  onSources: (sources: string[]) => void,
  onDone: () => void,
  onError: (error: Error) => void,
  accessToken: string | null,
): Promise<void> {
  if (!accessToken) {
    onError(new Error('인증이 필요합니다'));
    return;
  }

  const response = await fetch(QNA_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ policyId, question }),
  });

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
          const sourceStrings: string[] = (parsed.sources ?? []).map(
            (s: {
              policyId?: number;
              attachmentId?: number | null;
              attachmentLabel?: string | null;
              pageStart?: number | null;
              pageEnd?: number | null;
              excerpt?: string;
            }) => {
              const label = s.attachmentLabel ?? `정책 #${s.policyId}`;
              const page =
                s.pageStart && s.pageEnd ? ` p.${s.pageStart}-${s.pageEnd}` : '';
              const excerpt = s.excerpt ? ` — ${s.excerpt}` : '';
              return `${label}${page}${excerpt}`;
            },
          );
          onSources(sourceStrings);
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

  onDone();
}

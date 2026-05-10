export type QnaRole = 'user' | 'assistant';
export type QnaStatus = 'streaming' | 'done' | 'error';

export interface QnaSource {
  policyId: number;
  attachmentLabel: string | null;
  pageStart: number | null;
  pageEnd: number | null;
  excerpt: string | null;
}

export interface QnaMessage {
  id: string;
  role: QnaRole;
  content: string;
  sources?: QnaSource[];
  followUpQuestions?: string[];
  status: QnaStatus;
  /** assistant 메시지가 어느 user 질문에 속하는지 — retry 시 question 복원용 */
  questionRef?: string;
}

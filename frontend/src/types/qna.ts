export type QnaRole = 'user' | 'assistant';
export type QnaStatus = 'streaming' | 'done' | 'error';

export interface QnaMessage {
  id: string;
  role: QnaRole;
  content: string;
  sources?: string[];
  status: QnaStatus;
  /** assistant 메시지가 어느 user 질문에 속하는지 — retry 시 question 복원용 */
  questionRef?: string;
}

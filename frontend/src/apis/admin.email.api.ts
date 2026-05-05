import api from './client';

interface ApiEnvelope<T> { data: T }

export type EmailAttemptStatus = 'SENT' | 'DELIVERED' | 'BOUNCED' | 'COMPLAINED' | 'FAILED';
export type EmailAttemptType = 'DEADLINE' | 'RECOMMENDATION';

export interface EmailAttemptSummary {
  id: number;
  recipient: string;
  emailType: EmailAttemptType;
  status: EmailAttemptStatus;
  subject: string;
  sentAt: string;
  updatedAt: string;
  sesMessageId: string | null;
}

export interface EmailAttemptDetail extends EmailAttemptSummary {
  notificationHistoryId: number | null;
  recipientUserId: number | null;
  inputPayloadJson: string;
  errorCode: string | null;
  errorMessage: string | null;
  bounceType: string | null;
}

export interface EmailDailyStat extends Record<string, string | number> {
  date: string;
  sent: number;
  delivered: number;
  bounced: number;
  complained: number;
  failed: number;
}

export interface EmailKpi {
  today: { sent: number; delivered: number; bounced: number; failed: number };
  thisWeek: { sent: number; delivered: number; bounced: number; failed: number };
  successRate: number;
}

export interface EmailPreview {
  subject: string;
  htmlBody: string;
  textBody: string;
}

export interface ListFilter {
  from?: string;
  to?: string;
  statuses?: EmailAttemptStatus[];
  emailType?: EmailAttemptType;
  recipient?: string;
  page?: number;
  size?: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function listEmailAttempts(filter: ListFilter): Promise<Page<EmailAttemptSummary>> {
  const params = new URLSearchParams();
  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  if (filter.statuses?.length) params.set('statuses', filter.statuses.join(','));
  if (filter.emailType) params.set('emailType', filter.emailType);
  if (filter.recipient) params.set('recipient', filter.recipient);
  params.set('page', String(filter.page ?? 0));
  params.set('size', String(filter.size ?? 20));
  const res = await api.get(`v1/admin/email-attempts?${params}`).json<ApiEnvelope<Page<EmailAttemptSummary>>>();
  return res.data;
}

export async function getEmailAttempt(id: number): Promise<EmailAttemptDetail> {
  const res = await api.get(`v1/admin/email-attempts/${id}`).json<ApiEnvelope<EmailAttemptDetail>>();
  return res.data;
}

export async function getEmailDailyStats(from: string, to: string): Promise<EmailDailyStat[]> {
  const res = await api
    .get(`v1/admin/email-attempts/stats/daily?from=${from}&to=${to}`)
    .json<ApiEnvelope<EmailDailyStat[]>>();
  return res.data;
}

export async function getEmailKpi(): Promise<EmailKpi> {
  const res = await api.get('v1/admin/email-attempts/stats/kpi').json<ApiEnvelope<EmailKpi>>();
  return res.data;
}

export async function getEmailPreview(id: number): Promise<EmailPreview> {
  const res = await api
    .get(`v1/admin/email-attempts/${id}/preview`)
    .json<ApiEnvelope<EmailPreview>>();
  return res.data;
}

export async function redispatchEmail(id: number): Promise<{ newAttemptId: number }> {
  const res = await api
    .post(`v1/admin/email-attempts/${id}/redispatch`)
    .json<ApiEnvelope<{ newAttemptId: number }>>();
  return res.data;
}

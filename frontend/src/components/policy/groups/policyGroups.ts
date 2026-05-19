// policyGroups.ts
import type { LucideIcon } from 'lucide-react';
import { Users, Wallet, Pencil, Info } from 'lucide-react';

export type PolicyGroupId = 'eligibility' | 'benefits' | 'apply' | 'more';
export type PolicyGroupTone = 'brand' | 'amber' | 'success' | 'neutral';

export interface PolicyGroup {
  id: PolicyGroupId;
  label: string;
  description: string;
  Icon: LucideIcon;
  tone: PolicyGroupTone;
}

export const POLICY_GROUPS: PolicyGroup[] = [
  { id: 'eligibility', label: '받을 수 있는 사람', description: '이 정책을 받기 위한 조건을 알려드려요', Icon: Users, tone: 'brand' },
  { id: 'benefits', label: '받는 혜택', description: '어떤 지원을 받게 되는지 알려드려요', Icon: Wallet, tone: 'amber' },
  { id: 'apply', label: '신청하기', description: '어떻게 신청하는지 알려드려요', Icon: Pencil, tone: 'success' },
  { id: 'more', label: '더 알아보기', description: '문의·첨부·놓치기 쉬운 점·Q&A를 모았어요', Icon: Info, tone: 'neutral' },
];

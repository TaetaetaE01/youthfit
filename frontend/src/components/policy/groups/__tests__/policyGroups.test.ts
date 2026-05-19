// policyGroups.test.ts
import { describe, it, expect } from 'vitest';
import { POLICY_GROUPS, type PolicyGroupId } from '../policyGroups';

describe('POLICY_GROUPS', () => {
  it('4개의 그룹을 정의한다', () => {
    expect(POLICY_GROUPS).toHaveLength(4);
  });

  it('각 그룹은 id, label, description, tone을 가진다', () => {
    POLICY_GROUPS.forEach((g) => {
      expect(g.id).toBeTruthy();
      expect(g.label).toBeTruthy();
      expect(g.description).toBeTruthy();
      expect(g.tone).toBeTruthy();
    });
  });

  it('id는 eligibility, benefits, apply, more 순서', () => {
    const ids: PolicyGroupId[] = POLICY_GROUPS.map((g) => g.id);
    expect(ids).toEqual(['eligibility', 'benefits', 'apply', 'more']);
  });
});

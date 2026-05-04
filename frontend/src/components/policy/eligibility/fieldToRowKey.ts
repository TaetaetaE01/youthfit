const FIELD_TO_ROW_KEY: Record<string, string> = {
  age: 'age',
  region: 'region',
  legalDongCode: 'region',
  maritalStatus: 'marital',
  education: 'education',
  educationLevel: 'education',
  incomeMin: 'income',
  incomeMax: 'income',
  annualIncome: 'income',
  employmentKind: 'employment',
  employmentStatus: 'employment',
  majorField: 'major',
  specializationField: 'specialization',
};

export function fieldToRowKey(field: string): string | null {
  return FIELD_TO_ROW_KEY[field] ?? null;
}

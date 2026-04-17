import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type {
  MaritalStatus,
  Education,
  EmploymentKind,
  MajorField,
  SpecializationField,
} from '@/types/personalInfo';

export interface PersonalInfoFields {
  regionCode: string | null;
  regionDistrict: string | null;
  age: number | null;
  maritalStatus: MaritalStatus | null;
  incomeMin: number | null;
  incomeMax: number | null;
  education: Education | null;
  employmentKind: EmploymentKind | null;
  majorField: MajorField | null;
  specializationField: SpecializationField | null;
}

interface PersonalInfoState extends PersonalInfoFields {
  setField: <K extends keyof PersonalInfoFields>(key: K, value: PersonalInfoFields[K]) => void;
  setMany: (fields: Partial<PersonalInfoFields>) => void;
  resetField: (key: keyof PersonalInfoFields) => void;
  resetGroup: (keys: (keyof PersonalInfoFields)[]) => void;
  resetAll: () => void;
}

const initialFields: PersonalInfoFields = {
  regionCode: null,
  regionDistrict: null,
  age: null,
  maritalStatus: null,
  incomeMin: null,
  incomeMax: null,
  education: null,
  employmentKind: null,
  majorField: null,
  specializationField: null,
};

export const usePersonalInfoStore = create<PersonalInfoState>()(
  persist(
    (set) => ({
      ...initialFields,
      setField: (key, value) => set({ [key]: value } as Partial<PersonalInfoState>),
      setMany: (fields) => set(fields as Partial<PersonalInfoState>),
      resetField: (key) => set({ [key]: initialFields[key] } as Partial<PersonalInfoState>),
      resetGroup: (keys) => {
        const patch: Partial<PersonalInfoFields> = {};
        keys.forEach((k) => {
          (patch as Record<string, unknown>)[k] = initialFields[k];
        });
        set(patch as Partial<PersonalInfoState>);
      },
      resetAll: () => set(initialFields),
    }),
    {
      name: 'youthfit-personal-info',
      version: 1,
    },
  ),
);

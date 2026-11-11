export type PresetMultiplicidad = {
  id: string;
  label: string;
  minO: number;
  maxO: number | null;
  minD: number;
  maxD: number | null;
};

export const PRESETS_MULTIPLICIDAD: PresetMultiplicidad[] = [
  { id: '1:1', label: '1 : 1 (Uno a Uno)', minO: 1, maxO: 1, minD: 1, maxD: 1 },
  { id: '1:N', label: '1 : N (Uno a Muchos 0..*)', minO: 1, maxO: 1, minD: 0, maxD: null },
  {
    id: '1:1..*',
    label: '1 : 1..* (Uno a Muchos obligatorio)',
    minO: 1,
    maxO: 1,
    minD: 1,
    maxD: null,
  },
  { id: 'N:1', label: 'N : 1 (Muchos a Uno)', minO: 0, maxO: null, minD: 1, maxD: 1 },
  { id: 'N:M', label: 'N : M (Muchos a Muchos *..*)', minO: 0, maxO: null, minD: 0, maxD: null },
  { id: '0..1:1', label: '0..1 : 1 (Cero/Uno a Uno)', minO: 0, maxO: 1, minD: 1, maxD: 1 },
  { id: '0..1:N', label: '0..1 : N (Cero/Uno a Muchos)', minO: 0, maxO: 1, minD: 0, maxD: null },
];

export const OPCIONES_EXTREMO = [
  { valor: '0..1', min: 0, max: 1, label: '0..1 (Cero o uno)' },
  { valor: '1..1', min: 1, max: 1, label: '1..1 (Exactamente uno)' },
  { valor: '0..*', min: 0, max: null, label: '0..* (Cero a muchos / *)' },
  { valor: '1..*', min: 1, max: null, label: '1..* (Uno a muchos)' },
];

export function detectarPreset(
  minO: number | null,
  maxO: number | null,
  minD: number | null,
  maxD: number | null,
): string {
  const match = PRESETS_MULTIPLICIDAD.find(
    (p) => p.minO === minO && p.maxO === maxO && p.minD === minD && p.maxD === maxD,
  );
  return match?.id ?? 'custom';
}

export function detectarExtremo(min: number | null, max: number | null): string {
  if (min === 0 && max === 1) return '0..1';
  if (min === 1 && max === 1) return '1..1';
  if (min === 0 && max === null) return '0..*';
  if (min === 1 && max === null) return '1..*';
  return 'custom';
}

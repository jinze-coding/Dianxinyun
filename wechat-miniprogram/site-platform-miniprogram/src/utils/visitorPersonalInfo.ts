import { ref, watch, type Ref } from 'vue';
import type { VisitorPersonalInfo } from '../api/siteAccess';

type TextField = 'visitorCompany' | 'contactName' | 'contactPhone' | 'vehiclePlate';
type PersonalFields = Record<TextField, Ref<string>> & { travelMode: Ref<'DRIVING' | 'OTHER'> };
const textFields: TextField[] = ['visitorCompany', 'contactName', 'contactPhone', 'vehiclePlate'];

export function personalInfoPatch(info: VisitorPersonalInfo | undefined, edited: ReadonlySet<string>) {
  const patch: Partial<Record<TextField | 'travelMode', string>> = {};
  if (!info?.available) return patch;
  for (const field of textFields) {
    if (!edited.has(field)) patch[field] = info[field] || '';
  }
  if (!edited.has('travelMode')) patch.travelMode = info.travelMode === 'DRIVING' ? 'DRIVING' : 'OTHER';
  return patch;
}

export function useVisitorPersonalInfo(fields: PersonalFields) {
  const rememberInfo = ref(false);
  const personalInfoApplied = ref(false);
  const edited = new Set<string>();
  let applying = false;
  for (const field of [...textFields, 'travelMode'] as const) {
    watch(fields[field], () => { if (!applying) edited.add(field); }, { flush: 'sync' });
  }
  watch(rememberInfo, () => { if (!applying) edited.add('rememberInfo'); }, { flush: 'sync' });

  function applyPersonalInfo(info?: VisitorPersonalInfo) {
    if (!info || (edited.has('rememberInfo') && !rememberInfo.value)) return;
    const patch = personalInfoPatch(info, edited);
    applying = true;
    try {
      if (!edited.has('rememberInfo')) rememberInfo.value = info.rememberInfo;
      for (const field of textFields) {
        if (patch[field] !== undefined) fields[field].value = patch[field]!;
      }
      if (patch.travelMode) fields.travelMode.value = patch.travelMode === 'DRIVING' ? 'DRIVING' : 'OTHER';
      personalInfoApplied.value = Object.keys(patch).length > 0;
    } finally { applying = false; }
  }

  function resetPersonalInfo() {
    applying = true;
    rememberInfo.value = false;
    personalInfoApplied.value = false;
    edited.clear();
    applying = false;
  }

  function rememberChange(event: { detail: { value: string[] } }) {
    edited.add('rememberInfo');
    rememberInfo.value = event.detail.value.includes('remember');
  }

  return { rememberInfo, personalInfoApplied, applyPersonalInfo, resetPersonalInfo, rememberChange };
}

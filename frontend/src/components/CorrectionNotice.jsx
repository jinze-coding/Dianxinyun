export default function CorrectionNotice({ record }) {
  const message = record?.correctionNotice || record?.document?.correctionNotice || record?.material?.correctionNotice || record?.task?.correctionNotice;
  return message ? <div role="note" style={{ padding: '10px 14px', margin: '10px 0', borderRadius: 8, border: '1px solid #bfdbfe', background: '#eff6ff', color: '#1e40af', fontSize: 13 }}>{message}</div> : null;
}

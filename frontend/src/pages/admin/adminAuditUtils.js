export const auditActionTypes = [
  'USER_CREATED',
  'USER_UPDATED',
  'USER_ACTIVATED',
  'USER_DEACTIVATED',
  'USER_PASSWORD_RESET',
  'USER_BULK_UPLOAD',
  'ACADEMIC_YEAR_CREATED',
  'ACADEMIC_YEAR_UPDATED',
  'CLASS_CREATED',
  'CLASS_UPDATED',
  'CLASS_DELETED',
  'SUBJECT_CREATED',
  'SUBJECT_UPDATED',
  'SUBJECT_DELETED',
  'FACULTY_ASSIGNMENT_CREATED',
  'FACULTY_ASSIGNMENT_DELETED',
  'PARENT_STUDENT_LINK_CREATED',
  'PARENT_STUDENT_LINK_DELETED',
  'SCHOOL_SETTINGS_UPDATED',
  'SCHOOL_LOGO_UPDATED',
];

export function formatAuditTimestamp(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Unknown time';
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
    timeZone: 'Asia/Kolkata',
  }).format(date).replace(/\b(am|pm)\b/g, (period) => period.toUpperCase());
}

export function auditActionClasses(actionType = '') {
  const action = actionType.toUpperCase();
  if (/DELETE|DEACTIVATE/.test(action)) return 'bg-error/10 text-error';
  if (/PASSWORD_RESET/.test(action)) return 'bg-warning/15 text-app-text';
  if (/UPDATE|ACTIVATE/.test(action)) return 'bg-primary/10 text-primary';
  if (/CREATE|BULK_UPLOAD/.test(action)) return 'bg-success/10 text-success';
  return 'bg-muted/10 text-muted';
}

export function auditActionLabel(actionType = '') {
  return actionType
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function parseAuditValue(value) {
  if (value == null || value === '') return {};
  if (typeof value !== 'string') return value;
  try {
    return JSON.parse(value);
  } catch {
    return { value };
  }
}

function flattenValues(value, prefix = '', output = {}) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    Object.entries(value).forEach(([key, child]) => {
      const path = prefix ? `${prefix}.${key}` : key;
      if (/password|hash|token/i.test(key)) {
        output[path] = 'hidden';
      } else if (child && typeof child === 'object') {
        flattenValues(child, path, output);
      } else {
        output[path] = child == null ? '—' : String(child);
      }
    });
    return output;
  }
  if (Array.isArray(value)) {
    value.forEach((child, index) => flattenValues(child, `${prefix}[${index}]`, output));
    return output;
  }
  if (prefix) output[prefix] = value == null ? '—' : String(value);
  return output;
}

export function auditValueChanges(oldValue, newValue) {
  const before = flattenValues(parseAuditValue(oldValue));
  const after = flattenValues(parseAuditValue(newValue));
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])].sort();
  return keys.map((key) => ({
    key,
    before: before[key] ?? '—',
    after: after[key] ?? '—',
    changed: before[key] !== after[key],
  }));
}

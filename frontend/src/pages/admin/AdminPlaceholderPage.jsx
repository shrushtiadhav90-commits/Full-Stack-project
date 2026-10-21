import { useLocation } from 'react-router-dom';
import { EmptyState, PageHeader } from '../../components/ui';

const PAGES = {
  '/admin/users': ['Users', 'Manage student, parent, faculty, and administrator accounts.'],
  '/admin/classes': ['Classes', 'Manage academic years, classes, and sections.'],
  '/admin/subjects': ['Subjects', 'Manage the subjects available in the school.'],
  '/admin/assignments': ['Faculty Assignments', 'Assign faculty members to classes and subjects.'],
  '/admin/parent-links': ['Parent Links', 'Manage parent and student relationships.'],
  '/admin/settings': ['School Settings', 'Customize school branding and preferences.'],
  '/admin/audit-log': ['Audit Log', 'Review administrative changes and activity.'],
};

export default function AdminPlaceholderPage() {
  const { pathname } = useLocation();
  const [title, description] = PAGES[pathname] ?? ['Admin', 'Manage school administration.'];

  return (
    <>
      <PageHeader title={title} description={description} />
      <EmptyState
        title={`${title} coming soon`}
        message="This section is being prepared. Please check back soon."
      />
    </>
  );
}

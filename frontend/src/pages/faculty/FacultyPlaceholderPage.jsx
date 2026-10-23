/* eslint-disable react/prop-types */
import { BookOpen, ClipboardCheck, FileBarChart, GraduationCap, Users } from 'lucide-react';
import { PageHeader, EmptyState } from '../../components/ui';

const pageContent = {
  questions: {
    title: 'Question Bank',
    description: 'Create and organize reusable questions for your assessments.',
    emptyTitle: 'Your question bank is getting ready',
    emptyMessage: 'Questions you create will appear here. The question bank tools are coming in the next faculty update.',
    icon: BookOpen,
  },
  quizzes: {
    title: 'My Quizzes',
    description: 'Create, schedule, and manage your class quizzes.',
    emptyTitle: 'No quizzes to show yet',
    emptyMessage: 'Your quizzes and their current status will appear here.',
    icon: ClipboardCheck,
  },
  newQuiz: {
    title: 'Create a quiz',
    description: 'Build an assessment for your assigned classes and subjects.',
    emptyTitle: 'Quiz builder coming soon',
    emptyMessage: 'The quiz creation workflow will be available here soon.',
    icon: ClipboardCheck,
  },
  editQuiz: {
    title: 'Edit quiz',
    description: 'Review quiz details and assessment questions.',
    emptyTitle: 'Quiz editor coming soon',
    emptyMessage: 'Quiz editing tools will be available here soon.',
    icon: ClipboardCheck,
  },
  quizReport: {
    title: 'Quiz report',
    description: 'Review student results and quiz performance.',
    emptyTitle: 'Quiz reports coming soon',
    emptyMessage: 'Once reporting is available, performance insights for this quiz will appear here.',
    icon: FileBarChart,
  },
  grading: {
    title: 'Grading Queue',
    description: 'Review and grade short-answer submissions from your quizzes.',
    emptyTitle: 'Grading tools coming soon',
    emptyMessage: 'Submissions that need your review will appear in this queue.',
    icon: GraduationCap,
  },
  students: {
    title: 'Students',
    description: 'Explore student performance across your classes and subjects.',
    emptyTitle: 'Student insights coming soon',
    emptyMessage: 'Class lists and student performance details will appear here.',
    icon: Users,
  },
};

export default function FacultyPlaceholderPage({ page }) {
  const content = pageContent[page] ?? pageContent.quizzes;
  return (
    <div>
      <PageHeader title={content.title} description={content.description} />
      <EmptyState
        title={content.emptyTitle}
        message={content.emptyMessage}
        icon={content.icon}
      />
    </div>
  );
}

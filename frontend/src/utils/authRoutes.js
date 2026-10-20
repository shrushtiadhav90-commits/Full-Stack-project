export const dashboardPathForRole = (role) => {
  const paths = {
    STUDENT: '/student/dashboard',
    PARENT: '/parent/dashboard',
    FACULTY: '/faculty/dashboard',
    ADMIN: '/admin/dashboard',
  };
  return paths[role] ?? '/unauthorized';
};

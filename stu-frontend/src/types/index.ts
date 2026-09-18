export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT'
export type Status = 'ENABLED' | 'FROZEN' | 'SUSPENDED'
export interface User {
  id: number
  username: string
  name: string
  role: Role
  status: Status
  department?: string
  phone?: string
  photo?: string
}
export interface Student {
  id: number
  studentNo: string
  name: string
  gender: string
  phone?: string
  department: string
  major: string
  className: string
  enrollmentYear: number
  status: Status
  earnedCredits: number
  requiredCredits: number
  gpa: number
}
export interface Staff {
  id: number
  username: string
  name: string
  role: 'ADMIN' | 'TEACHER'
  department?: string
  phone?: string
  photo?: string
  status: Status
}
export interface Course {
  id: number
  code: string
  name: string
  teacherId: number
  teacherName: string
  credit: number
  hours: number
  semester: string
  schedule: string
  location: string
  capacity: number
  enrolled: number
  status: 'UNPUBLISHED' | 'PUBLISHED'
  coverUrl?: string
  description?: string
  gradeStatus?: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'PUBLISHED'
  usualWeight?: number
  midtermWeight?: number
  finalWeight?: number
  myEnrollmentStatus?: string
}
export interface Enrollment {
  id: number
  courseId: number
  courseName: string
  teacherName: string
  semester: string
  credit: number
  schedule: string
  location: string
  status: string
}
export interface GradeRecord {
  id: number
  studentId: number
  studentNo: string
  studentName: string
  usualScore?: number
  midtermScore?: number
  finalScore?: number
  score?: number
  examStatus: 'NORMAL' | 'ABSENT' | 'DEFERRED' | 'CHEATING'
}
export interface Notification {
  id: number
  title: string
  content: string
  createdAt: string
  read: boolean
}
export interface Media {
  id: number
  title: string
  url: string
  kind: 'image' | 'video'
  courseId?: number
  author?: string
  authorName?: string
  authorId?: number
  ownerId?: number
  createdAt?: string
}
export interface DashboardData {
  stats: { label: string; value: number | string; suffix?: string }[]
  creditsByDepartment: { name: string; value: number }[]
  gradeDistribution: { name: string; value: number }[]
  courseEnrollment: { name: string; value: number; capacity: number }[]
  recentActivities: { id: number; title: string; content: string; createdAt: string }[]
  cache: { backend: string; hit: boolean; ttlSeconds: number }
}

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:9090',
    [string]$AdminPassword = '123456',
    [string]$TeacherPassword = '123456',
    [string]$StudentPassword = '123456',
    [switch]$AllowRedisDown
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$admin = $null
$studentProfileId = $null
$studentFrozen = $false
$failure = $null

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body,
        [string]$Token,
        [int[]]$ExpectedHttpStatus = @(200)
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $args = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; SkipHttpErrorCheck = $true }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $args.ContentType = 'application/json; charset=utf-8'
        $args.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    } elseif ($Method -in @('POST', 'PUT', 'DELETE')) {
        # Spring endpoints with an optional @RequestBody still reject PowerShell's
        # default form content type, so send an explicit empty JSON object.
        $args.ContentType = 'application/json; charset=utf-8'
        $args.Body = '{}'
    }
    $response = Invoke-WebRequest @args
    if ($response.StatusCode -notin $ExpectedHttpStatus) {
        throw "$Method $Path returned HTTP $($response.StatusCode), expected $($ExpectedHttpStatus -join ', '). Body: $($response.Content)"
    }
    $content = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
    $json = if ($content) { $content | ConvertFrom-Json } else { $null }
    return [pscustomobject]@{ StatusCode = $response.StatusCode; Json = $json; Content = $content }
}

function Invoke-SuccessApi {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token)
    $arguments = @{ Method = $Method; Path = $Path; Token = $Token }
    if ($PSBoundParameters.ContainsKey('Body')) { $arguments.Body = $Body }
    $result = Invoke-Api @arguments
    if (-not $result.Json -or [int]$result.Json.code -ne 202) {
        throw "$Method $Path did not return business success (code 202). Body: $($result.Content)"
    }
    return $result.Json.data
}

function Login([string]$Username, [string]$Password, [string]$Role) {
    $data = Invoke-SuccessApi -Method POST -Path '/api/auth/login' -Body @{ username = $Username; password = $Password; role = $Role }
    if (-not $data.token) { throw "Login for $Username did not return a token." }
    return $data
}

try {
    $health = Invoke-Api -Method GET -Path '/actuator/health' -ExpectedHttpStatus @(200, 503)
    if ($health.StatusCode -eq 503) {
        if (-not $AllowRedisDown) { throw "Backend health is DOWN: $($health.Content). Restore Redis, or use -AllowRedisDown only for core-business diagnosis." }
        Write-Warning 'Redis health is DOWN; running the core business workflow only. This is not Redis verification.'
    } elseif ($health.Json.status -ne 'UP') { throw "Backend health is not UP: $($health.Content)" }

    Write-Host '1/11 Logging in all three roles...'
    $admin = Login 'admin' $AdminPassword 'ADMIN'
    $teacher = Login 'teacher01' $TeacherPassword 'TEACHER'
    $student = Login 'student01' $StudentPassword 'STUDENT'

    $stamp = Get-Date -Format 'yyMMddHHmmss'
    $courseCode = "SMK$stamp"
    Write-Host "2/11 Teacher creates isolated smoke course $courseCode..."
    $course = Invoke-SuccessApi -Method POST -Path '/api/courses' -Token $teacher.token -Body @{
        code = $courseCode; name = "自动化验收课程-$stamp"; credit = 2; hours = 32; semester = '2026-2027-1'
        schedule = '周一 1-2 节'; location = '综合楼 101'; capacity = 5; description = '自动化 smoke 测试记录，可安全保留。'
    }
    if (-not $course.id) { throw 'Course creation did not return course id.' }

    Write-Host '3/11 Teacher publishes course; student enrolls, withdraws, and re-enrolls...'
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/publish" -Token $teacher.token | Out-Null
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/enroll" -Token $student.token | Out-Null
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/withdraw" -Token $student.token | Out-Null
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/enroll" -Token $student.token | Out-Null

    Write-Host '4/11 Teacher enters grades and submits them...'
    $gradeView = Invoke-SuccessApi -Method GET -Path "/api/courses/$($course.id)/grades" -Token $teacher.token
    # Account IDs and student-profile IDs are intentionally distinct. This isolated
    # course has exactly one enrollment, so its only roster record is unambiguous.
    $record = @($gradeView.records) | Select-Object -First 1
    if (-not $record) { throw 'The enrolled student was not present in the teacher grade roster.' }
    $gradePayload = @{
        usualWeight = 30; midtermWeight = 30; finalWeight = 40
        records = @(@{ id = $record.id; studentId = $record.studentId; usualScore = 88; midtermScore = 86; finalScore = 90; examStatus = 'NORMAL' })
    }
    Invoke-SuccessApi -Method PUT -Path "/api/courses/$($course.id)/grades" -Token $teacher.token -Body $gradePayload | Out-Null
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/grades/submit" -Token $teacher.token | Out-Null

    Write-Host '5/11 Verifying teacher cannot approve submitted grades...'
    Invoke-Api -Method POST -Path "/api/courses/$($course.id)/grades/approve" -Token $teacher.token -ExpectedHttpStatus @(403) | Out-Null

    Write-Host '6/11 Administrator rejects; teacher resubmits; administrator approves and publishes...'
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/grades/reject" -Token $admin.token -Body @{ reason = 'smoke test: resubmit path' } | Out-Null
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/grades/submit" -Token $teacher.token | Out-Null
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/grades/approve" -Token $admin.token | Out-Null
    Invoke-SuccessApi -Method POST -Path "/api/courses/$($course.id)/grades/publish" -Token $admin.token | Out-Null

    Write-Host '7/11 Verifying the student result and earned credits...'
    $before = Invoke-SuccessApi -Method GET -Path '/api/grades/mine?semester=2026-2027-1' -Token $student.token
    $published = @($before.records | Where-Object { $_.courseName -eq $course.name }) | Select-Object -First 1
    if (-not $published -or $null -eq $published.score) { throw 'Published grade was not visible to the student.' }
    $creditsBefore = [decimal]$before.earnedCredits
    if ($creditsBefore -lt [decimal]$course.credit) { throw "Earned credits ($creditsBefore) did not include the published course." }

    Write-Host '8/11 Verifying a repeated publish cannot duplicate credits...'
    $repeat = Invoke-Api -Method POST -Path "/api/courses/$($course.id)/grades/publish" -Token $admin.token -ExpectedHttpStatus @(200)
    if ($repeat.Json.code -notin @(202, 505)) { throw "Unexpected repeated publish result: $($repeat.Content)" }
    $after = Invoke-SuccessApi -Method GET -Path '/api/grades/mine?semester=2026-2027-1' -Token $student.token
    if ([decimal]$after.earnedCredits -ne $creditsBefore) { throw "Repeated publish changed earned credits from $creditsBefore to $($after.earnedCredits)." }

    Write-Host '9/11 Verifying a student cannot use an administrator endpoint...'
    $forbidden = Invoke-Api -Method POST -Path '/api/students' -Token $student.token -Body @{ name = 'forbidden'; department = 'test' } -ExpectedHttpStatus @(403)
    if ($forbidden.StatusCode -ne 403) { throw 'Expected HTTP 403 for student administrator request.' }

    Write-Host '10/11 Freezing the student invalidates the current JWT, then restoring the seed account...'
    $students = Invoke-SuccessApi -Method GET -Path '/api/students?page=1&size=10&keyword=20260001' -Token $admin.token
    $studentProfile = @($students.records | Where-Object { $_.studentNo -eq '20260001' }) | Select-Object -First 1
    if (-not $studentProfile) { throw 'Could not locate the student profile for the frozen-token check.' }
    $studentProfileId = $studentProfile.id
    Invoke-SuccessApi -Method PUT -Path "/api/students/$studentProfileId/status" -Token $admin.token -Body @{ status = 'FROZEN' } | Out-Null
    $studentFrozen = $true
    Invoke-Api -Method GET -Path '/api/auth/me' -Token $student.token -ExpectedHttpStatus @(401) | Out-Null
    Invoke-SuccessApi -Method PUT -Path "/api/students/$studentProfileId/status" -Token $admin.token -Body @{ status = 'ENABLED' } | Out-Null
    $studentFrozen = $false

    Write-Host '11/11 PASS: full three-role workflow, withdrawal, grade review cycle, idempotency, and authorization verified.'
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($studentFrozen -and $admin -and $studentProfileId) {
        try {
            Invoke-SuccessApi -Method PUT -Path "/api/students/$studentProfileId/status" -Token $admin.token -Body @{ status = 'ENABLED' } | Out-Null
            Write-Warning 'Restored the frozen seed student after a failed smoke test.'
        } catch { Write-Warning "Could not restore the seed student automatically: $($_.Exception.Message)" }
    }
}

if ($failure) { Write-Error "SMOKE TEST FAILED: $failure"; exit 1 }

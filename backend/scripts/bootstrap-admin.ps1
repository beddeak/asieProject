$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $env:AEGIS_BOOTSTRAP_NAME = Read-Host '최초 관리자 아이디 (기본 admin)'
    if ([string]::IsNullOrWhiteSpace($env:AEGIS_BOOTSTRAP_NAME)) { $env:AEGIS_BOOTSTRAP_NAME = 'admin' }
    $env:AEGIS_BOOTSTRAP_EMAIL = Read-Host '관리자 이메일 (새 계정만 필요)'
    $securePassword = Read-Host '비밀번호 (기존 계정이면 기존 비밀번호)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try { $env:AEGIS_BOOTSTRAP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    & .\gradlew.bat bootJar --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw '빌드에 실패했습니다.' }
    & java -jar build/libs/aegisvault-0.0.1-SNAPSHOT.jar --spring.profiles.active=bootstrap-admin @args
    if ($LASTEXITCODE -ne 0) { throw '최초 관리자 설정에 실패했습니다.' }
}
finally {
    Remove-Item Env:AEGIS_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}

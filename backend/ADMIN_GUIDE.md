# 사용자 관리

관리자 화면은 `/admin/users`, 문서 활동 기록은 `/admin/activity`입니다.
로그인한 사용자의 현재 DB 직급이 `ADMIN`이고 계정 상태가 `ACTIVE`여야 이용할 수 있습니다.
로그인 후 메인 보관소 홈페이지로 이동하며, 관리자는 관리실 메뉴에서 사용자 관리 화면을 열 수 있습니다.

## 제공 기능

- 아이디·이메일 검색, 부서·직급·계정 상태 필터, 페이지당 20명 조회
- 직급 임명 및 소속 부서 변경·미배정 처리
- 밴, 잠금, 정상 복구, 계정 삭제
- 기존 `departmentcreate.html`의 `departmentForm`을 재사용한 부서 생성
- 전체 부서의 문서 작성·열람·검토 화면 열람·승인·반려 기록 조회
- 문서 활동의 사용자·문서·활동 종류·검색어 필터, 페이지당 30건 조회

## 설계와 범위

`ADMIN` 직급 계정이 새 문서를 등록하면 검토 대기를 거치지 않고 `APPROVED` 상태로 저장됩니다.
작성한 관리자를 승인자로 기록하고 승인 시각 및 작성·승인 활동 기록을 함께 남깁니다.
부서 배정과 정상 계정 조건은 동일하며, 일반 계정의 문서는 기존 검토 대기 흐름을 사용합니다.
검토함에서 자기 문서를 수동 승인하는 것은 계속 제한됩니다.

`AdminUserController`는 폼 검증과 화면 이동, `AdminUserService`는 권한·상태 변경을 담당합니다.
입력은 `UserAssignmentRequest`에 받으며 사용자의 비밀번호·아이디·계정 상태를 임명 폼에서 변경할 수 없습니다.
각 서비스에서도 현재 DB 사용자의 권한을 검사합니다. 화면이나 세션의 직급만으로 허용하지 않습니다.

삭제는 `DELETED`로 변경하는 논리 삭제입니다. 작성자·검토자와 연결된 사용자 행, 문서, 활동 기록을 보존하며
로그인을 영구 차단합니다. 삭제된 계정의 복구·재임명은 허용하지 않습니다. 밴과 잠금은 정상 복구할 수 있습니다.
현재 저장된 상태값과 호환되도록 정지 상태의 DB 이름은 기존 `BAN`을 유지했습니다.
`db/schema.sql`은 기존 H2 ENUM 컬럼에 `DELETED`를 추가합니다. 기존 상태와 데이터는 보존하고,
빈 DB에서는 JPA가 테이블을 만들 수 있도록 테이블이 있을 때만 확장합니다.

관리자는 자기 직급을 내리거나 자기 계정을 정지·잠금·삭제할 수 없습니다.
관리 변경 시 관리자 행을 ID 순서대로 잠가, 동시에 서로를 정지하는 작업이 마지막 정상 관리자를 없애지 못하게 합니다.
`CurrentAccountFilter`는 매 요청에 DB 상태와 직급을 반영하여 밴·삭제·강등을 기존 세션에도 적용합니다.
변경 직전에 이미 실행 중이던 요청을 강제로 중단하지는 않습니다.

문서 활동은 `DocumentActivity`에 활동 시점의 사용자·직급·문서 제목·버전·시간을 저장합니다.
비밀번호나 문서 본문을 기록하지 않습니다. 문서 작업과 기록 저장은 같은 트랜잭션에서 성공하거나 롤백됩니다.
계정 삭제 또는 직급 변경 후에도 이전 기록의 정보가 유지됩니다.
기록 기능 도입 전의 열람 기록은 복원할 수 없고, 현재 기록은 성공한 문서 작업을 대상으로 합니다.
권한 거부, 로그인 실패, 사용자 관리 변경 기록과 감사 로그 무결성 검사는 향후 AuditLog 단계의 범위입니다.

신규 패키지는 `admin`, `activity`, `security`처럼 소문자입니다.
기존 `User`, `Department`, `Document` 패키지는 이번 작업에서 일괄 변경하지 않았습니다.

## 실행과 첫 관리자

Java 21에서 `backend`를 작업 디렉터리로 하여 실행합니다.

```powershell
.\gradlew.bat bootRun
```

화면 주소: `http://localhost:8081/admin/users`
회원가입은 항상 `STAFF` 계정을 생성합니다. 공용 기본 관리자 계정이나 비밀번호는 추가하지 않습니다.
처음 사용할 때 관리자 계정이 없다면, 가입한 본인 계정을 애플리케이션 종료 후 개발 DB에서 한 번 임명합니다.
DB URL은 `backend` 기준 `jdbc:h2:file:./data/aegisvault`, 사용자는 `sa`입니다.

```sql
UPDATE users SET position = 'ADMIN' WHERE nickname = '본인이 가입한 아이디' AND account_status = 'ACTIVE';
```

DB 도구에서 영향받은 행이 정확히 1개인지 확인하고 다시 실행합니다.
실제 계정에 대한 이 초기 임명 작업은 테스트 과정에서 자동 수행하지 않습니다.

## 검증

```powershell
.\gradlew.bat clean build
```

테스트는 H2 메모리 DB를 사용합니다. `AdminUserIntegrationTest`는 실제 Spring Security·Thymeleaf·JPA를 함께 검증하며
권한 위조, CSRF, 임명·상태 변경·삭제, 기존 세션 차단, 동시 관리자 변경, 문서 기록 보존과 롤백을 확인합니다.

이 PC의 관리된 실행 환경에서 Java의 `Unable to establish loopback connection` 오류가 발생하면,
해당 PowerShell 세션에만 다음 설정을 적용한 뒤 빌드합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Users/GIGAFACTORY/asieProject/backend/.gradle'
.\gradlew.bat build --no-daemon
```

# 통합 기능과 변경 내역

문서 작성·검토만 이어지던 구조를 프로젝트의 변경 요청, 품질시험, 보안 검토, 최종 배포까지 연결했습니다. Java 21 / Spring Boot / Thymeleaf 모놀리스를 유지하며 로컬에서 외부 서비스 없이 업무를 진행합니다.

원격 `main`의 홈 화면 개편·최근 문서 검색·관리자 문서 즉시 승인 변경도 통합했습니다. 기존 부서 탭, 공지와 문서 목록 주소는 유지합니다. 사용자·로그·DB 파일은 구현이나 테스트를 위해 교체하지 않았습니다.

## 어디를 바꿨는지

Java 경로는 `src/main/java/com/asie/aegisvault/`, 화면 경로는 `src/main/resources/templates/` 기준입니다. 전체 파일별 추가·수정 구분은 [CHANGED_FILES.md](CHANGED_FILES.md)에 있습니다.

| 기능 | 핵심 코드 | 화면·동작 |
| --- | --- | --- |
| 문서 검색·필터·정렬·페이지 | `Document/DocumentFilter`, `DocumentVersionRepository`, `DocumentService`, `common/SearchText`, `web/Pagination` | `documentlist.html`, 홈 검색과 최근 6건; `q`와 기존 `keyword` 검색 호환 |
| 초안·버전·이력·보관 | `Document/Document`, `DocumentVersion`, `DocumentLocks`, `DocumentWorkflowController` | `documenteditor.html`, `documentdetail.html`, `documenthistory.html`; 초안만 수정, 제출본 보존 |
| 관리자 즉시 승인·일반 검토 | `Document/DocumentService`, `ReviewNote`, `security/DocumentAccess` | 모든 관리자 제출 문서 즉시 승인, 일반 문서 기술·보안 검토, 반려 사유·의견 기록 |
| 첨부파일 | `attachment/AttachmentService`, `FileStore`, `FileRetention` | 초안 업로드·삭제, 권한 확인 후 다운로드, 새 버전 첨부 복사·해시 검증 |
| 워터마크 | `Document/DocumentPage`, `DocumentExport` | 상세 화면·문서 HTML에 사용자·시각·버전 표시 |
| 프로젝트·역할·필수 문서 | `project/ProjectService`, `ProjectAccess`, `ProjectMember`, `ProjectRequirement` | `projects.html`, `projectform.html`, `projectdetail.html`, `projectmembers.html` |
| 프로젝트 문서 접근 | `security/DocumentAccess`, `DocumentVersionRepository` | 참여 역할·부서·직급·보안 등급을 함께 확인, 목록과 상세의 기준 공유 |
| 연구개발 변경 요청 | `workflow/EngineeringChange`, `WorkflowService` | `projectwork.html` 변경 요청 탭; 기준 버전·담당자·승인된 후속 버전·처리 근거 |
| 품질·부적합·재시험 | `workflow/QualityCheck`, `QualityRun`, `QualityResult`, `Nonconformity` | 시험 기준, 적합 판정, 부적합 보고서, 재시험 연결과 시정 조치 |
| 보안 검토 | `security/SecurityClassification`, `workflow/SecurityAssessment`, `ProjectEvidence` | 문서 보안 승인 및 프로젝트 구성별 독립 보안 판정 |
| 임시 접근 | `access/TemporaryAccessService`, `TemporaryAccess` | `access.html`; 요청·승인·거절·만료·회수, 열람 범위만 부여 |
| 배포 게이트·스냅샷·회수 | `release/ReleaseGate`, `ReleaseService`, `ReleaseController` | 차단 사유, 승인된 정확한 버전·수신자 고정, ZIP 다운로드와 회수 |
| 전체·부서 공지 | `notice/AnnouncementService`, `DepartmentNotice`, `DepartmentWorkspaceController` | 전체 공지와 부서 공지 분리; 부서 생성 즉시 공통 탭에 표시 |
| 부서 운영 | `Department/DepartmentLifecycle`, `DepartmentAdminController` | `admindepartments.html`; 이름 변경, 사용자·업무 이관 후 폐쇄 |
| 알림·내 업무 | `notification/NotificationService`, `NotificationController`, `audit/BusinessEventListener` | `notifications.html`, `tasks.html`; 담당자 알림과 권한별 할 일 |
| 계정·복구·세션 | `account/AccountService`, `AdministratorBootstrap`, `security/AccountPrincipal`, `CurrentAccountFilter` | 비밀번호 변경, 일회용 복구 링크, 기존 세션 무효화, 최초 관리자 CLI |
| 감사·타임라인·무결성 | `audit/AuditChain`, `AuditRecord`, `AuditHead`, `SecurityAudit` | `audit.html`, `projecttimeline.html`; HMAC 연결, 업무와 감사의 원자적 저장 |
| 공통 화면·오류 | `web/WorkspaceAdvice`, `WorkspaceAccount`, `Pagination`, `config/SecurityConfig` | 공통 메뉴·CSRF·CSP·오류 화면·모바일 대응, 홈과 업무 화면의 공통 색상 |
| DB 이전·실행 | `config/LocalDatabaseMigration`, `db/migration/V1*`, `V2*`, `application.yaml`, `build.gradle` | 기존 H2 자동 백업 후 Flyway 이전, FK 무결성, Hibernate 스키마 검증 |

## 실무 관점에서 바꾼 구조

- **목록은 목록 데이터만 조회합니다.** 문서 본문과 사용자 비밀번호를 목록 모델에 담지 않습니다. 검색·권한 필터·정렬·페이지 처리는 SQL에서 수행합니다. 홈도 같은 조회 정책을 사용합니다.
- **상태 제한은 엔티티에 둡니다.** 초안 편집, 검토, 보관, 시험 결과, 배포 회수 등은 엔티티의 상태 전이 메서드를 거칩니다. 불필요한 `DocumentContentRules`나 새 문서 본문 길이 제한은 도입하지 않았습니다. 입력 DTO는 폼 형식, 공유 접근 정책은 여러 진입점의 권한 검사를 담당합니다.
- **동시 변경은 충돌로 처리합니다.** 프로젝트 → 문서 순서의 DB 잠금과 버전 번호·수정 리비전 검사를 사용합니다. 새 버전 중복 생성, 중복 배포, 오래된 폼 덮어쓰기를 막습니다. 비밀번호 복구 토큰은 사용자 잠금을 획득한 뒤 다시 조회해 동시 재사용을 차단합니다.
- **검토 근거는 문서 구성에 연결합니다.** 문서 버전·분류·보안 등급·첨부 해시·필수 문서·시험 기준의 지문을 저장합니다. 이후 구성이 달라지면 이전 품질·보안 결과로 배포할 수 없습니다.
- **권한은 서버의 현재 DB 상태를 기준으로 합니다.** URL·숨김 필드·오래된 세션의 역할로 권한이 올라가지 않습니다. 첨부, 과거 버전, 업무 보고서, ZIP 다운로드도 재검사합니다. 민감한 품질·보안 보고서는 프로젝트 문서의 직급·보안 범위를 충족해야 열람합니다.
- **업무 변경과 감사 기록을 함께 커밋합니다.** 업무 롤백 시 감사도 롤백합니다. 로그인 실패·접근 거부는 별도로 보존합니다. 감사 체인은 DB 기준점 잠금으로 기록 순서를 보장합니다.
- **첨부 원본은 정적 공개 경로 밖에 보관합니다.** 사용자 파일명을 저장 경로로 쓰지 않습니다. 파일 형식·크기·해시를 확인하며 이전 버전이 참조한 원본은 남깁니다.
- **기존 데이터 구조는 명시적으로 이전합니다.** 운영 시작마다 임의로 테이블을 수정하는 `ddl-auto:update`를 Flyway와 `validate`로 교체했습니다. DB 외래 키는 Java 객체 그래프를 불필요하게 늘리지 않으면서 참조 무결성을 지킵니다.

## 검증

Java 21에서 `build`와 **264개 테스트**를 실행하여 실패·오류·건너뜀 0건으로 통과했습니다. 원격 main에 있던 실제 H2 파일의 복사본에서도 자동 백업 → V1/V2 이전 → Hibernate 검증 → 서버 기동을 확인했습니다. 기존 테스트와 원격에서 추가된 테스트를 유지하면서 새 DTO·필수 반려 사유·관리자 승인 정책에 맞게 통합했습니다. 테스트를 끄거나 운영 권한 검사를 완화하지 않았습니다.

- `WorkflowIntegrationTest`: 문서 수명주기·권한·첨부, 변경 해결, 품질 부적합·재시험, 보안 검토, 배포·ZIP·회수, 임시 권한 만료, 알림 소유권, 복구·세션 무효화, 감사 변조·롤백, 동시 요청, 관리자 즉시 승인과 배포 게이트를 검증합니다.
- `SchemaMigrationTest`: 새 DB 생성, 기존 사용자 비밀번호·문서·공지 보존, 재실행, 파일 DB 자동 백업, FK 위반 차단을 검증합니다.
- 기존 문서·관리자·부서·홈 테스트: 현재 권한, CSRF, 페이지 경계, 검색 문자 이스케이프, HTML 이스케이프, 실제 템플릿 렌더링, 목록 SQL의 본문·비밀번호 제외를 확인합니다.
- Chromium 실제 브라우저: 로그인·회원가입·부서 생성·참여자 배정·프로젝트 생성·초안·첨부·관리자 즉시 승인·품질시험·독립 보안 검토·배포 ZIP 다운로드·회수 후 410 응답을 확인했습니다. 데스크톱과 390px 모바일 화면도 확인했습니다.

외부 운영 배포, 대규모 부하, SMTP, 악성코드 스캐너, 원본 첨부 워터마크는 검증 또는 연동 범위에 포함하지 않았습니다. 적용 범위와 백업·복구 절차는 [LOCAL_GUIDE.md](LOCAL_GUIDE.md)에 명시했습니다.

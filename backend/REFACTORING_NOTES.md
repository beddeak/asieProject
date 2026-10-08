# 운영 관점 코드 개선 내역

전체 코드를 점검한 뒤, 목록 조회 시 불필요한 데이터 로딩과 페이지 범위 오류, 문서 입력 규칙 불일치, 부서 생성 후 재전송 문제를 개선했습니다.

## 1. 목록 전용 조회 모델

기존 검토함은 `DocumentVersion` 엔티티와 연결된 사용자 엔티티를 조회하여 목록에서 사용하지 않는 문서 본문과 비밀번호 해시도 가져왔습니다.
사용자 관리 역시 `User` 엔티티를 템플릿 모델에 전달했습니다.

검토함은 `ReviewQueueItem`, 사용자 목록은 `UserListItem` 생성자 프로젝션을 사용하여 표시할 열만 SQL에서 선택합니다.
부서 이름과 작성자 이름은 조회 시 조인하여 가져오므로 화면 렌더링 중 추가 조회에 의존하지 않습니다.
관리자 화면의 현재 사용자 모델도 ID와 아이디 문자열로 바꿨습니다. 권한 확인·로그인은 기존과 같이 현재 사용자 정보를 별도로 확인합니다.

부서·직급·상태 조건, 자기 문서 검토 제외, 최신 버전 선택은 DB 조회 단계에서 유지합니다.
사용자 검색의 `%`, `_`, `\`는 와일드카드가 아닌 검색 문자로 처리합니다.

## 2. 공통 페이지 경계 처리

기존 사용자 목록·활동 기록·검토함은 `page=2147483647`에서 JPA의 정수 오프셋 범위를 넘길 수 있었습니다.
존재하지 않는 페이지에서도 현재 페이지가 전체 페이지 수보다 크게 표시될 수 있었습니다.

`PageQueries.fetch`가 문서 목록·검토함·사용자 목록·활동 기록·부서 공지 목록의 페이지 처리를 담당합니다.

- 음수 페이지: 첫 페이지로 보정
- 매우 큰 페이지: JPA 오프셋 범위를 먼저 제한하고, 결과의 전체 건수에 따라 마지막 페이지 재조회
- 검색 결과 없음: 0번 빈 페이지 반환
- 정렬: 각 목록의 날짜와 ID를 함께 사용하여 같은 시각의 데이터도 순서 유지

서비스의 권한 및 검색 조건은 보정 후 재조회에도 그대로 적용합니다. 페이지 크기는 기존의 20건, 활동 기록 30건입니다.

## 3. 문서 입력 규칙 통일

기존 화면·요청 DTO는 제목을 50자로 제한했지만 엔티티·DB는 255자를 허용했고, 본문에는 길이 제한이 없었습니다.
`DocumentContentRules`에서 제목 255자, 본문 100,000자라는 공통 규칙을 정의했습니다.

요청 DTO의 Bean Validation, 화면의 `maxlength`와 안내 문구, 서비스 진입점, 엔티티 생성자가 같은 제한을 사용합니다.
직접 서비스 호출에서도 검증한 다음 저장을 시작하므로 잘못된 입력으로 문서·버전·활동 기록이 일부만 남지 않습니다.
기존 문서 열람에는 새 입력 제한을 소급 적용하지 않습니다. 스키마 변경이나 데이터 변환은 없습니다.

## 4. 부서 생성 후 화면 이동

`POST /dep/create`는 생성 성공 후 `GET /departments/{id}`로 리다이렉트하고 성공 안내를 표시합니다.
브라우저 새로고침은 게시판 GET을 반복하므로 생성 POST가 재전송되지 않습니다.
검증 오류와 중복 이름 오류는 기존처럼 입력 폼에서 안내합니다. 관리자 화면의 `/admin/departments` 흐름은 그대로 이용할 수 있습니다.

## 파일별 변경 내역

아래 경로는 `backend/` 기준입니다.

| 구분 | 파일 | 변경 목적 |
| --- | --- | --- |
| 추가 | `src/main/java/com/asie/aegisvault/common/PageQueries.java` | 공통 페이지 범위 보정 |
| 추가 | `src/main/java/com/asie/aegisvault/Document/dto/ReviewQueueItem.java` | 검토함 조회 전용 DTO |
| 추가 | `src/main/java/com/asie/aegisvault/admin/UserListItem.java` | 비밀번호를 포함하지 않는 사용자 목록 DTO |
| 추가 | `src/main/java/com/asie/aegisvault/Document/DocumentContentRules.java` | 공통 문서 입력 제한과 도메인 검증 |
| 수정 | `src/main/java/com/asie/aegisvault/Document/DocumentVersionRepository.java` | 검토함의 조회 열을 필요한 항목으로 제한 |
| 수정 | `src/main/java/com/asie/aegisvault/User/UserRepository.java` | 사용자 관리용 프로젝션 및 검색 조건 |
| 수정 | `src/main/java/com/asie/aegisvault/Document/DocumentService.java` | 입력 규칙 적용, 문서 목록·검토함의 페이지 처리 |
| 수정 | `src/main/java/com/asie/aegisvault/admin/AdminUserService.java` | 사용자 목록 DTO, 사용자·활동 기록 페이지 처리 |
| 수정 | `src/main/java/com/asie/aegisvault/notice/DepartmentNoticeService.java` | 부서 공지의 공통 페이지 처리 |
| 수정 | `src/main/java/com/asie/aegisvault/admin/AdminUserController.java` | 현재 사용자 엔티티 대신 표시용 값 전달 |
| 수정 | `src/main/java/com/asie/aegisvault/Document/DocumentController.java` | 문서 폼에 공통 입력 제한 전달 |
| 수정 | `src/main/java/com/asie/aegisvault/Document/DocumentVersion.java` | 도메인 생성 시 공통 검증 |
| 수정 | `src/main/java/com/asie/aegisvault/Document/dto/DocumentCreateRequest.java` | 요청 검증에 공통 제한 적용 |
| 수정 | `src/main/java/com/asie/aegisvault/Department/DepartmentController.java` | 생성 후 게시판으로 리다이렉트 |
| 수정 | `src/main/resources/templates/adminusers.html` | 사용자 목록 DTO와 표시용 모델 반영 |
| 수정 | `src/main/resources/templates/documentreview.html` | 검토함 DTO 반영 |
| 수정 | `src/main/resources/templates/documentwrite.html` | 입력 제한과 안내 문구를 서버 값에서 렌더링 |
| 추가 | `src/test/java/com/asie/aegisvault/support/SqlCapture.java` | 실제 SQL의 조회 열 검증 도구 |
| 수정 | `src/test/java/com/asie/aegisvault/admin/AdminUserIntegrationTest.java` | SQL·페이지 경계·입력 제한·생성 후 새로고침 검증 |
| 수정 | `src/test/java/com/asie/aegisvault/Document/DocumentReviewIntegrationTest.java` | 본문·해시 제외, 권한과 페이지 경계 검증 |
| 수정 | `src/test/java/com/asie/aegisvault/Document/DocumentControllerTest.java` | 검토함 DTO와 제목 제한 반영 |
| 수정 | `src/test/java/com/asie/aegisvault/Document/dto/DocumentCreateRequestTest.java` | 입력 제한의 경계값 검증 |
| 수정 | `src/test/java/com/asie/aegisvault/Department/DepartmentControllerTest.java` | 부서 생성 성공 시 이동 주소 검증 |
| 수정 | `src/test/java/com/asie/aegisvault/notice/DepartmentWorkspaceIntegrationTest.java` | 부서 생성의 리다이렉트 흐름 반영 |
| 수정 | `ADMIN_GUIDE.md` | 동작·입력 제한·문서 링크 갱신 |
| 추가 | `REFACTORING_NOTES.md` | 개선 이유와 전체 변경 파일 기록 |

## 검증

Java 21에서 전체 빌드와 210개 테스트가 통과했습니다(실패·오류·건너뜀 0건).
H2 메모리 DB에서 실제 JPA SQL과 Spring Security·Thymeleaf 렌더링을 검증합니다.
`SqlCapture`는 SQL 구조만 기록하고 파라미터 값은 기록하지 않습니다.

- 목록 SQL에서 비밀번호 해시와 문서 본문이 조회되지 않는지 확인
- 잘못된 페이지 번호, 빈 결과, 검색 필터, 최신 버전·부서·직급 제한 확인
- 제목 255자·본문 100,000자 저장 성공과 초과 입력 거절, 저장 전 실패 시 부분 기록 없음 확인
- 부서 생성 후 GET 새로고침으로 부서가 추가 생성되지 않는지 확인

Java 21에서 `backend`를 작업 디렉터리로 사용합니다.

```powershell
.\gradlew.bat build --no-daemon
```

# 버그 점검 및 수정 · 2026-10-09 KST

점검 기준: `155c6fd` (`feat/local-project-workflows`). 기존 테스트가 통과한 상태에서 권한 회수, 계정 복구, 부서 이관의 경계 조건을 추가로 검사했습니다.

## 확인한 버그

| 우선순위 | 재현 조건과 영향 | 수정 |
| --- | --- | --- |
| 높음 | 품질 담당자의 보안 등급을 기밀보다 낮게 내리면 업무 화면은 403으로 차단되지만, 부적합 종료 POST는 302로 성공했습니다. 화면을 볼 수 없는 사용자가 보고서를 종료할 수 있었습니다. | 모든 연구개발·품질·보안 변경 요청에 프로젝트 문서 범위·직급·보안 등급 검사를 적용했습니다. 거부된 요청은 업무 데이터를 변경하지 않습니다. |
| 높음 | 일회용 복구 링크 발급 후 본인이 정상적으로 비밀번호를 변경해도 기존 링크로 다시 비밀번호를 바꿀 수 있었습니다. | 비밀번호가 변경되는 같은 트랜잭션에서 미완료 복구 요청을 모두 종료합니다. 비밀번호 변경이 실패하면 복구 링크는 그대로 유효합니다. |
| 보통 | 부서를 이관·폐쇄하면 사용자·문서·프로젝트만 옮겨지고 공지는 폐쇄된 부서에 남았습니다. 이관받은 부서에서 기존 공지를 조회하면 404가 발생했습니다. 공지만 남은 부서도 빈 부서로 취급했습니다. | 부서 공지도 함께 이관합니다. 작성자·본문·작성 시각은 보존하며 전체 공지는 이동하지 않습니다. 공지가 있는 부서는 이관 대상이 필요합니다. 공지 생성·수정·삭제와 부서 이관이 같은 부서 잠금을 사용하게 했습니다. |

## 변경 파일

- [WorkflowService.java](src/main/java/com/asie/aegisvault/workflow/WorkflowService.java): 변경 요청의 공통 권한 검사. 품질·보안 판정의 중복 문서 조회는 제거했습니다.
- [AccountService.java](src/main/java/com/asie/aegisvault/account/AccountService.java): 모든 비밀번호 변경 경로에서 복구 요청 종료.
- [DepartmentLifecycle.java](src/main/java/com/asie/aegisvault/Department/DepartmentLifecycle.java): 공지를 이관·빈 부서 판단·감사 기록에 포함.
- [DepartmentNotice.java](src/main/java/com/asie/aegisvault/notice/DepartmentNotice.java), [DepartmentNoticeRepository.java](src/main/java/com/asie/aegisvault/notice/DepartmentNoticeRepository.java): 공지 소속 변경과 이관 대상 잠금 조회.
- [DepartmentNoticeService.java](src/main/java/com/asie/aegisvault/notice/DepartmentNoticeService.java): 공지 변경과 이관 간 잠금 공유, 폐쇄된 부서의 관리 버튼 표시 수정.
- [admindepartments.html](src/main/resources/templates/admindepartments.html): 공지 이관과 보존 항목 안내.
- [WorkflowIntegrationTest.java](src/test/java/com/asie/aegisvault/WorkflowIntegrationTest.java): 재현·회귀 테스트 5개 추가.

## 검증

수정 전에는 정상 비밀번호 변경 후 복구 링크 무효화, 이관 후 공지 조회, 공지만 있는 부서 폐쇄 제한의 3개 테스트가 실패했습니다. 권한 회수 후 직접 변경 요청을 막는 테스트도 별도로 실패하는 것을 확인했습니다.

**최종 결과: 전체 빌드 및 269개 테스트 통과, 실패·오류·건너뜀 0건.** 실행 중인 서버에서도 로그인·회원가입 렌더링, 입력 검증, CSRF 거부, 보호된 화면 접근 차단을 확인했습니다.

회귀 테스트는 실제 Spring Security·MVC·JPA·Flyway를 사용합니다. 금지된 요청의 403 응답뿐 아니라 보고서·시험 기준이 변경되지 않았는지, 공지의 내용과 원래 작성자가 보존됐는지, 전체 공지 소속이 유지되는지도 검사합니다.

관리자 문서 즉시 승인 정책은 그대로 적용됩니다. 이번 점검은 확인된 경계 조건을 수정한 것으로, 모든 운영 조건에서 버그가 없다는 보증은 아닙니다.

# AegisVault

Java 21 · Spring Boot · Thymeleaf 기반의 문서·프로젝트 업무 시스템입니다. 별도 프런트엔드 서버나 외부 DB 없이 로컬 H2 파일 DB로 실행합니다.

```powershell
cd backend
.\gradlew.bat bootRun
```

Linux/macOS에서는 `cd backend` 후 `bash gradlew bootRun`을 실행합니다. 기본 포트는 **8081**, 접속 범위는 본인 PC입니다.

- [실행·관리자 설정·업무 진행 안내](backend/LOCAL_GUIDE.md)
- [기능별 변경 파일과 설계 이유](backend/FEATURE_CHANGES.md)
- [추가 버그 점검·수정 내역](backend/BUGFIX_REVIEW.md)
- [문서 변경 후 배포 차단·시연·코드 설명](backend/DOCUMENT_CHANGE_GUIDE.md)
- [공통 화면·단계별 할 일·승인본·배포·부적합 시연](backend/WORKFLOW_GUIDE.md)
- [관리자 권한과 부서 운영](backend/ADMIN_GUIDE.md)
- [업무 구조와 구현 범위](backend/IMPLEMENTATION_PLAN.md)

전체 검증은 `backend`에서 `bash gradlew build --no-daemon` 또는 `.\gradlew.bat build --no-daemon`으로 실행합니다. 결과는 `backend/build/reports/tests/test/index.html`에 생성됩니다.

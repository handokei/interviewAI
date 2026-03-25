# InterviewAI Backend — 프로젝트 컨텍스트

## 기술 스택
- Spring Boot 3.5.0 + Java 17
- PostgreSQL
- Spring AI (Claude Sonnet 4.6 — `claude-sonnet-4-6`)
- Spring Security (OAuth2 GitHub/Google + JWT)
- Gradle

## 주요 설정
- 백엔드 포트: 8080
- CORS 허용 origin: http://localhost:5173
- Hibernate DDL: `update`
- Multipart: max-file-size 20MB, max-request-size 50MB, Tomcat max-post-size 50MB

## GitHub
- 저장소: `handokei/interviewAI`
- 이슈/PR 대상 브랜치: `dev` (main은 운영 준비 전 절대 병합 금지)

---

## 패키지 구조

```
com.example.project
 ├── domain     # 도메인 모듈 계층
 ├── global     # 공통 모듈 계층
 └── infra      # 인프라 연동 계층
```

**global 하위 패키지**
```
global
├── common
├── config
├── error
├── util
└── security
```

**domain 하위 패키지**
```
domain
├── controller
│   └── dto
├── service
│   ├── usecase
│   └── facade
├── domain
│   ├── model
│   └── repository
└── exception
```

---

## Git 컨벤션

### 브랜치 전략

| 브랜치 | 용도 |
|--------|------|
| `main` | 배포 브랜치 |
| `release` | 배포 전 테스트용 브랜치 (QA용) |
| `dev` | 통합 개발 브랜치 |
| `feat/기능명` | 기능 작업 브랜치 |

- 브랜치 생성 전 반드시 GitHub Issue 먼저 생성
- **이슈 생성 시 브랜치 prefix에 맞는 라벨을 반드시 설정**:

  | 브랜치 prefix | GitHub 라벨 |
  |---------------|-------------|
  | `feat/` | `feat` |
  | `fix/` | `fix` |
  | `refactor/` | `refactor` |
  | `chore/` | `chore` |
  | `docs/` | `documents` |
  | `test/` | `chore` |
  | `comment/` | `chore` |
  | `rename/` | `refactor` |
  | `remove/` | `chore` |
  | `style/` | `chore` |

- 브랜치 명명:

  | 브랜치 prefix | 용도 |
  |---------------|------|
  | `feat/기능명` | 새로운 기능 추가 |
  | `fix/버그명` | 버그 수정 |
  | `refactor/작업명` | 리팩토링 (기능 변경 X) |
  | `test/테스트명` | 테스트 코드 추가/수정 |
  | `chore/작업명` | 빌드 설정 변경 |
  | `docs/문서명` | 문서 수정 |
  | `comment/작업명` | 주석 추가 및 변경 |
  | `rename/작업명` | 파일 혹은 폴더명 수정 |
  | `remove/작업명` | 파일 삭제 |
  | `style/작업명` | 코드 포맷팅, 스타일 변경 (논리 변경 X) |

- 작업 흐름: feature 브랜치 → PR → `dev`
- **PR 전 필수 순서**: `./gradlew test` → `./gradlew build` → PR 생성
- 테스트 커버리지 100% 유지

### PR 생성 전 Test Plan 사전 공유 (필수)

**PR을 생성하기 전에 반드시 사용자에게 Test Plan 항목을 먼저 공유할 것.**
사용자가 로컬에서 직접 확인한 후 PR을 생성한다.

Test Plan 공유 형식:
```
PR 전 직접 확인해주세요:
- [ ] 항목 1
- [ ] 항목 2
```

### 커밋 타입

| 타입 | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변경 X) |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드 설정 변경 |
| `docs` | 문서 수정 (README 등) |
| `comment` | 주석 추가 및 변경 |
| `rename` | 파일 혹은 폴더명 수정 |
| `remove` | 파일 삭제 |
| `style` | 코드 포맷팅, 세미콜론 등 스타일 변경 (논리 변경 X) |
| `merge` | 파일 병합 |

### 커밋 메시지 형식

```
<type>(<issue>): <subject>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>
```

**예시**
```
feat(#1): 댓글 목록을 포함하는 게시글 검색 API 구현
fix(#2): 유효하지 못한 게시글 생성 요청의 잘못된 응답 문구 수정
refactor(#4): 불변성을 가져야 하는 입력 파라미터에 final 처리
test(#5): Enum으로 정의된 상태 코드의 문서 생성을 위한 TC 작성
chore(#7): 미사용 설정 파일 삭제
```

### 커밋 세분화 규칙
- `Co-Authored-By: Claude` 절대 포함 금지
- **레이어 단위로 커밋 세분화**: Repository → Service 인터페이스 → ServiceImpl → Controller → Test
- 한 커밋 = 한 레이어 또는 한 역할

---

## 자바 코드 스타일

### 패키지 이름

- 연속된 소문자 단어로 구성
- 허용: `com.example.deepspace`
- 불가: `com.example.deepSpace`

### 클래스 이름
- `UpperCamelCase`
- 클래스: **명사 혹은 명사구** (e.g. `UserService`)
- 인터페이스: **명사/명사구** 또는 **형용사/형용사구** (e.g. `Readable`)

### 메소드 이름
- `lowerCamelCase`
- **동사 혹은 동사구**로 표현 (e.g. `sendMessage`, `stop`)
- JUnit 테스트 메소드는 `_`(언더스코어)로 논리 컴포넌트 구분 가능

### 상수 이름
- `CONSTANT_CASE` — 모두 대문자, 단어 구분은 `_`

### 상수가 아닌 필드 / 파라미터 이름
- `lowerCamelCase` — **명사 혹은 명사구** (e.g. `computedValues`)
- public 메서드에서 한 글자 파라미터는 피할 것 (e.g. `String a` 불가)

### 테스트 메소드 이름
- 기능/예외 구분을 앞에 명시
  - `기능_테스트_회원_정보를_수정한다`
  - `예외_테스트_이메일이_중복되었다`

---

## 완료된 도메인
- Auth (OAuth2 GitHub/Google + JWT)
- User 관리
- Document 업로드 (PDF 파싱 → 텍스트 DB 저장, S3 미사용)
  - 다중 업로드, documentType PATCH, FILE_TOO_LARGE 핸들링
- Interview 세션 + Claude AI 연동
  - 페이지네이션, 취소(cancel), 통계(stats)

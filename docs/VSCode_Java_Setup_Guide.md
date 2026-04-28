# VS Code Java 개발 환경 셋팅 & 실행 가이드

## 1. 사전 요구사항

### JDK 설치
> **아래 경로/버전은 예시입니다. 프로젝트별 `build.gradle`의 `sourceCompatibility`를 확인하세요.**

- [Adoptium](https://adoptium.net/) 또는 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) 설치
- 설치 후 환경변수 확인:
```powershell
java -version
# 프로젝트에서 요구하는 버전이 나와야 함
```

### JAVA_HOME 환경변수 설정
```
시스템 환경변수 → JAVA_HOME → C:\Program Files\Eclipse Adoptium\jdk-xx.x.x (설치 경로)
시스템 환경변수 → Path → %JAVA_HOME%\bin 추가
```

---

## 2. VS Code 확장(Extension) 설치

### 필수 확장
| 확장명 | ID | 설명 |
|--------|-----|------|
| Extension Pack for Java | `vscjava.vscode-java-pack` | Java 개발 필수 패키지 (아래 포함) |
| Language Support for Java | `redhat.java` | Java 언어 지원, 자동완성, 에러 체크 |
| Debugger for Java | `vscjava.vscode-java-debug` | Java 디버깅 |
| Gradle for Java | `vscjava.vscode-gradle` | Gradle 빌드 지원 |

### 설치 방법
1. `Ctrl+Shift+X` → Extensions 패널 열기
2. `Extension Pack for Java` 검색 → Install
3. `Gradle for Java` 검색 → Install

### 선택 확장 (권장)
| 확장명 | ID | 설명 |
|--------|-----|------|
| Spring Boot Extension Pack | `vmware.vscode-boot-dev-pack` | Spring Boot 개발 지원 |
| Lombok Annotations Support | `vscjava.vscode-lombok` | Lombok 지원 (이 프로젝트에서 사용 중) |

### 테마 확장 (IntelliJ 스타일)
| 확장명 | ID | 설명 |
|--------|-----|------|
| IntelliJ IDEA New UI Theme | `niccolofulljames.intellij-idea-new-ui` | **현재 사용 중.** IntelliJ New UI 스타일 테마 |
| Familiar Java Themes | `zerodind.familiar-java-themes` | IntelliJ Darcula 스타일 Java 전용 테마 (semantic highlighting 지원) |

테마 적용: `Ctrl+K Ctrl+T` → 설치한 테마 선택

### nullAnalysis 빨간줄 주의
`java.compile.nullAnalysis.mode`를 `"automatic"`으로 설정하면 `@NonNull`/`@Nullable` 없는 코드에 전부 빨간줄이 표시됨.
또한 JiBX 커스텀 jar(`jibx-run-rc0.jar`)의 `IMarshallable.JiBX_getName()` 추상 메서드 미구현도 에러로 잡힘.
**IntelliJ와 동일하게 하려면 반드시 `"disabled"`로 설정할 것.**

---

## 3. VS Code Java 설정 (IntelliJ 동일 셋팅)

### settings.json 설정
`Ctrl+Shift+P` → `Preferences: Open Settings (JSON)` → 아래 내용 적용:

> **아래 JDK 경로/버전은 예시입니다. IntelliJ의 Project Structure → SDKs에서 실제 경로를 확인하세요.**

```json
{
    // ─── JDK 경로 (예시 — 본인 환경에 맞게 수정) ────────────────
    "java.jdt.ls.java.home": "C:\\Users\\gjdks\\.jdks\\ms-17.0.18",

    // ─── 런타임 설정 ─────────────────────────────────────────────
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-17",
            "path": "C:\\Users\\gjdks\\.jdks\\ms-17.0.18",
            "default": true
        }
    ],

    // ─── 빌드 설정 ───────────────────────────────────────────────
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.import.gradle.enabled": true,
    "java.import.gradle.wrapper.enabled": true,

    // ─── 컴파일러 검사 수준 ──────────────────────────────────────
    // [핵심] Null 분석 비활성화 — IntelliJ와 동일하게 하려면 disabled 필수
    "java.compile.nullAnalysis.mode": "disabled"
}
```

### 설정 항목별 IntelliJ 대응 설명

| VS Code 설정 | IntelliJ 대응 | 설명 |
|-------------|--------------|------|
| `java.jdt.ls.java.home` | Project SDK | Language Server가 사용할 JDK 경로 |
| `java.configuration.runtimes` | Platform Settings → SDKs | 프로젝트에서 사용할 JDK 런타임 목록 |
| `java.configuration.updateBuildConfiguration` | Auto-Import | build.gradle 변경 시 자동 재임포트 |
| `java.import.gradle.wrapper.enabled` | Gradle → Use Gradle Wrapper | gradlew 사용 여부 |
| `java.compile.nullAnalysis.mode` | (기본 비활성) | Null 분석 수준. **disabled 필수** |

### JDK 경로 찾는 법 (IntelliJ에서 확인)
1. IntelliJ → `File` → `Project Structure` → `SDKs`
2. JDK home path 확인 (예: `C:\Users\gjdks\.jdks\ms-17.0.18`)
3. 해당 경로를 `java.jdt.ls.java.home`과 `java.configuration.runtimes[].path`에 설정

> **주의**: JDK 경로는 각 PC 환경에 따라 다름. IntelliJ에서 실제 사용 중인 경로를 확인해서 넣을 것

### Gradle Wrapper
- 이 프로젝트는 **Gradle 8.7** 사용 (gradle-wrapper.properties 기준)
- Gradle을 따로 설치할 필요 없음 → `gradlew` (Wrapper)가 알아서 다운로드함

---

## 4. 프로젝트 열기 & 초기 셋팅

### 프로젝트 열기
```
File → Open Folder → 프로젝트 루트 폴더 선택 (예: C:\Project\OTAX-CORE)
```

### 처음 열면 일어나는 일
1. VS Code가 `build.gradle` 감지 → Java 프로젝트로 인식
2. Gradle Wrapper가 의존성 다운로드 (처음엔 시간 걸림)
3. 우측 하단에 "Java: Ready" 표시되면 셋팅 완료

### 프로젝트 인식 안 될 때
```
Ctrl+Shift+P → Java: Clean Java Language Server Workspace → Restart
```

---

## 5. 빌드

### 터미널에서 Gradle 빌드
```powershell
# 프로젝트 빌드
./gradlew build

# 빌드 (테스트 스킵)
./gradlew build -x test

# 클린 빌드
./gradlew clean build

# WAR 패키징
./gradlew bootWar
```

### VS Code GUI에서 빌드
1. 좌측 사이드바 → Gradle (코끼리 아이콘) 클릭
2. 프로젝트명 → `Tasks` → `build` → `build` 더블클릭

---

## 6. 실행

### 방법 1: Spring Boot Dashboard (권장)
1. 좌측 사이드바 → Spring Boot Dashboard 아이콘
2. 해당 모듈 항목에서 ▶ (Run) 클릭

### 방법 2: main 클래스에서 직접 실행
1. `*Application.java` 파일 열기 (각 모듈의 메인 클래스)
2. `main` 메서드 위에 나타나는 `Run | Debug` 링크 클릭

### 방법 3: 터미널에서 실행
```powershell
# Spring Boot 실행
./gradlew bootRun

# 프로파일 지정 실행
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 실행 확인
- 기본 포트로 서버 시작됨
- 브라우저에서 `http://localhost:포트번호` 접속 확인

---

## 7. 디버깅

### 브레이크포인트 설정
- 코드 줄번호 왼쪽 클릭 → 빨간 점 표시 = 브레이크포인트

### 디버그 실행
1. `main` 메서드 위 `Debug` 클릭 또는
2. `F5` 키 → 디버그 모드 실행

### 디버그 조작
| 단축키 | 동작 |
|--------|------|
| `F5` | 계속 실행 (Continue) |
| `F10` | 한 줄 실행 (Step Over) |
| `F11` | 함수 안으로 진입 (Step Into) |
| `Shift+F11` | 함수 밖으로 나감 (Step Out) |
| `Ctrl+Shift+F5` | 재시작 (Restart) |
| `Shift+F5` | 중지 (Stop) |

### launch.json 설정 (선택)
`.vscode/launch.json` 파일 생성으로 디버그 설정 커스터마이징 가능:
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "MyApplication",
            "request": "launch",
            "mainClass": "com.example.MyApplication",
            "args": "--spring.profiles.active=local"
        }
    ]
}
```
> mainClass는 각 프로젝트 모듈의 실제 메인 클래스로 변경

---

## 8. 테스트

### 터미널에서 테스트
```powershell
# 전체 테스트
./gradlew test

# 특정 테스트 클래스 (예시)
./gradlew test --tests "com.example.MyApplicationTests"
```

### VS Code에서 테스트
1. 테스트 파일 열기
2. 테스트 메서드 위 `Run Test | Debug Test` 클릭
3. 또는 좌측 사이드바 Testing 아이콘 → 테스트 목록에서 실행

---

## 9. 자주 쓰는 단축키

| 단축키 | 기능 |
|--------|------|
| `Ctrl+Shift+P` | 명령 팔레트 |
| `Ctrl+P` | 파일 빠른 열기 |
| `Ctrl+T` | 심볼 검색 (클래스, 메서드) |
| `F12` | 정의로 이동 (Go to Definition) |
| `Shift+F12` | 참조 찾기 (Find References) |
| `Ctrl+Shift+O` | 현재 파일 심볼 목록 |
| `Ctrl+.` | Quick Fix (import 자동추가 등) |
| `Shift+Alt+F` | 코드 포맷팅 |
| `Ctrl+Space` | 자동완성 |

---

## 10. AI 코딩 도구

### GitHub Copilot
- VS Code에서 AI 코드 자동완성 및 채팅 기능 제공
- 설치: `Ctrl+Shift+X` → `GitHub Copilot` 검색 → Install
- 채팅: `Ctrl+Shift+I` (Copilot Chat 패널)
- 인라인 제안: 코드 작성 중 자동으로 회색 텍스트 표시 → `Tab`으로 수락
- Agent 모드: 채팅에서 `@workspace` 등을 활용해 프로젝트 전체 맥락 기반 작업 가능
- `docs/agent-context.md`를 Copilot에 읽히면 프로젝트 컨텍스트를 빠르게 복구 가능

### Claude Code (터미널 기반)
- Anthropic의 CLI 코딩 에이전트. 터미널에서 `claude` 명령으로 실행
- 파일 읽기/쓰기/검색/터미널 명령 등을 자율적으로 수행
- VS Code와 병행 사용 시 Claude Code가 파일을 직접 편집하고, VS Code에서 결과 확인하는 흐름
- 복잡한 멀티파일 리팩토링이나 대규모 코드 생성에 유용

### 사용 팁
- **코드 편집/빌드/디버그는 IntelliJ**에서, **AI 채팅/자동완성은 VS Code Copilot**에서 병행 가능
- Agent에게 컨텍스트를 줄 때는 `docs/agent-context.md`, `docs/dataact-dev-notes.md` 참조
- 코드 수정 권한 범위는 `agent-context.md`의 규칙을 따름

---

## 11. 트러블슈팅

### "Java 17 or more recent is required"
→ `java.jdt.ls.java.home`이 프로젝트에서 요구하는 JDK 버전을 가리키는지 확인

### import 에러 (빨간줄)
→ `Ctrl+Shift+P` → `Java: Clean Java Language Server Workspace` → Restart

### Gradle 의존성 안 받아질 때
```powershell
./gradlew --refresh-dependencies
```

### Lombok 관련 에러 (@Getter, @Setter 등 인식 안 됨)
→ `vscjava.vscode-lombok` 확장 설치 확인

### 빌드 캐시 문제
```powershell
./gradlew clean
# 그래도 안 되면
Remove-Item -Recurse -Force .gradle, build
./gradlew build
```

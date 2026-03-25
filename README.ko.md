# MineCast

마인크래프트 서버에서 TTS(텍스트 음성 변환) 오디오를 모든 플레이어에게 동시에 방송하는 Fabric 모드입니다. 서버 관리자가 `/cast <텍스트>`를 입력하면, 서버가 Typecast TTS API를 호출하고 모든 접속 중인 플레이어에게 음성이 재생됩니다.

> **[Typecast](https://typecast.ai?utm_source=minecast&utm_medium=github) 기반** — 다양한 AI 보이스를 제공하는 TTS 플랫폼

[English README](README.md)

---

## 동작 원리

```
/cast 안녕하세요
     │
     ▼
서버 모드가 Typecast API 호출 → MP3 수신
     │
     ▼
MP3를 청크로 분할 → 커스텀 패킷으로 모든 플레이어에게 전송
     │
     ▼
클라이언트 모드가 청크 수신 → MP3 디코딩 → OpenAL로 재생
```

---

## 요구 사항

- 마인크래프트 **26.1**
- Fabric Loader **0.18.4+**
- Fabric API **0.143.15+26.1**
- Java **25**
- [Typecast](https://typecast.ai) API 키 및 voice ID

---

## 설치 방법

### 서버

1. 마인크래프트 26.1용 [Fabric Loader 0.18.4](https://fabricmc.net/use/server/) 설치
2. [Releases](https://github.com/jaebong-human/minecast/releases/latest)에서 최신 JAR를 다운로드해 서버의 `mods/` 폴더에 추가:
   - `minecast-server-<버전>.jar`
   - `fabric-api-<버전>+26.1.jar`
3. 서버 시작 — `config/minecast.json`이 자동으로 생성됩니다
4. `config/minecast.json`에 API 정보 입력 ([설정](#설정) 참고)
5. 서버 재시작

### 클라이언트

TTS 음성을 듣고 싶은 플레이어는 클라이언트 모드를 설치해야 합니다:

1. 마인크래프트 26.1용 [Fabric Loader 0.18.4](https://fabricmc.net/use/installer/) 설치
2. [Releases](https://github.com/jaebong-human/minecast/releases/latest)에서 최신 JAR를 다운로드해 `mods/` 폴더에 추가:
   - `minecast-client-<버전>.jar`
   - `fabric-api-<버전>+26.1.jar`

> 클라이언트 모드를 설치하지 않은 플레이어도 서버에 접속해서 플레이할 수 있습니다. 단, TTS 음성이 들리지 않습니다.

---

## API 키 발급 방법

1. [typecast.ai](https://typecast.ai?utm_source=minecast&utm_medium=github)에서 회원가입
2. [개발자 → API → API Keys](https://typecast.ai/developers/api/api-key?utm_source=minecast&utm_medium=github) 페이지에서 API 키 생성
3. [개발자 → API → 캐릭터](https://typecast.ai/developers/api/voices?utm_source=minecast&utm_medium=github)에서 원하는 캐릭터를 선택하고 **Voice ID** 복사

---

## 설정

`config/minecast.json` (첫 서버 시작 시 자동 생성):

```json
{
  "apiKey": "Typecast API 키",
  "voiceId": "기본 voice ID",
  "maxTextLength": 200
}
```

| 항목 | 설명 |
|------|------|
| `apiKey` | Typecast API 키 |
| `voiceId` | 사용할 Voice ID (기본값: `tc_60e5426de8b95f1d3000d7b5` — 잭) |
| `maxTextLength` | 입력 텍스트 최대 글자 수 |

---

## 명령어

| 명령어 | 권한 | 설명 |
|--------|------|------|
| `/cast <텍스트>` | OP 레벨 2+ | 기본 voice로 모든 플레이어에게 TTS 방송 |
| `/cast <텍스트> --voice <voice_id>` | OP 레벨 2+ | 특정 voice로 TTS 방송 |

### 예시

```
/cast 서버에 오신 것을 환영합니다!
/cast 드래곤이 처치되었습니다! --voice tc_abc123
```

---

## 이벤트 연동

커맨드 블록을 활용하면 코드 수정 없이 맵의 특정 이벤트에 TTS를 연동할 수 있습니다.

**커맨드 블록 예시:**
```
cast 마을이 침략당했습니다!
```

커맨드 블록 옆에 버튼이나 레드스톤 신호를 연결해 활성화하세요.

---

## 소스에서 빌드

```bash
git clone https://github.com/jaebong-human/minecast.git
cd minecast
./gradlew :minecast-server:build
./gradlew :minecast-client:build
```

빌드된 JAR 출력 위치:
- `minecast-server/build/libs/minecast-server-<버전>.jar`
- `minecast-client/build/libs/minecast-client-<버전>.jar`

---

## 라이선스

MIT

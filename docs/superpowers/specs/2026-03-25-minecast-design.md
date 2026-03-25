# Minecast — 설계 문서

**날짜:** 2026-03-25
**상태:** 승인됨

---

## 개요

관리자가 `/cast <텍스트>` 명령어를 실행하면 Typecast TTS API를 호출해 MP3 오디오를 생성하고, 월드의 모든 플레이어에게 커스텀 패킷으로 전송하여 인게임에서 재생하는 시스템.

---

## 컴포넌트 구조

```
minecast/
├── minecast-server/   ← Paper 플러그인 (서버 설치)
└── minecast-client/   ← Fabric 모드 (플레이어 클라이언트 설치)
```

**마인크래프트 버전:** 1.21.x
**서버:** Paper
**클라이언트:** Fabric

---

## 전체 흐름

```
관리자: /cast 안녕하세요
    ↓
[minecast-server] (비동기 스레드)
  1. Typecast API HTTP POST → MP3 바이트 전체 버퍼링
  2. MP3를 32KB 청크로 분할
  3. 월드의 모든 플레이어에게 병렬로 전송:
     START → CHUNK×N → END
    ↓ (Plugin Messaging Channel: minecast:audio)
[minecast-client] (각 플레이어)
  4. 청크 버퍼에 누적
  5. END 수신 → JLayer MP3 디코딩 → OpenAL(LWJGL 번들) 재생
```

**중요:** 서버는 Typecast 응답을 완전히 버퍼링한 후에 START 패킷을 전송한다. 스트리밍 전송 없음.

---

## 패킷 프로토콜

채널: `minecast:audio`

모든 패킷의 첫 번째 바이트는 **타입 식별자**다.

| 타입 바이트 | 패킷 이름 | 추가 필드 | 설명 |
|------------|----------|----------|------|
| `0x00` | `START` | `totalBytes: int`, `chunkCount: int` | 수신 준비 신호 |
| `0x01` | `CHUNK` | `index: int`, `data: byte[]` | 최대 32KB 데이터 |
| `0x02` | `END` | (없음) | 재생 시작 신호 |

**패킷 크기:** Paper Plugin Messaging Channel 최대 허용치는 1MB. 32KB 청크는 이 범위 내.
**청크 검증:** 클라이언트는 END 수신 시 수신한 청크 수가 `chunkCount`와 일치하는지 확인한다. 불일치 시 버퍼 리셋 및 경고.

---

## Typecast API 연동

### 1단계: TTS 생성 요청
```
POST {typecast.api-url}
Content-Type: application/json
Authorization: Bearer {typecast.api-key}

{
  "text": "<텍스트>",
  "actor_id": "{typecast.actor-id}",   ← config.yml에서 읽음
  "lang": "auto",
  "xapi_hd": true,
  "model_version": "latest"
}
```

응답:
```json
{
  "result": {
    "speak_v2_url": "https://..."
  }
}
```

### 2단계: MP3 다운로드
`speak_v2_url`에 GET 요청 → MP3 바이트 직접 수신.

**speak_v2_url 가용성:** Typecast는 URL을 즉시 사용 가능한 상태로 반환한다. 폴링 불필요. 단, GET 요청 타임아웃은 30초로 설정하고, 타임아웃 시 API 실패로 처리한다.

---

## 서버 플러그인 (minecast-server)

### 명령어
- `/cast <텍스트>` — 권한 노드: `minecast.use` (기본: OP 전용)
- OP가 아니더라도 `minecast.use` 권한을 명시적으로 부여받으면 사용 가능

### config.yml
```yaml
typecast:
  api-key: "YOUR_API_KEY"
  actor-id: "YOUR_ACTOR_ID"
  api-url: "https://typecast.ai/api/speak"

cast:
  cooldown-seconds: 10      # 글로벌 쿨다운 (서버 전체 단위, 관리자별 아님)
  max-text-length: 200
```

### 처리 로직
1. 권한 확인, 글로벌 쿨다운 확인 (서버 전체 쿨다운), 텍스트 길이 확인
2. **고정 크기 스레드 풀(10개)** 로 비동기 전환 (`Executors.newFixedThreadPool(10)`)
3. Typecast API 2단계 호출 (타임아웃 30초) → MP3 전체 버퍼링
4. MP3를 32KB 청크로 분할
5. 현재 월드의 모든 온라인 플레이어에게 **병렬로** 패킷 전송 — 플레이어별 독립 전송, 한 플레이어의 지연/접속 끊김이 다른 플레이어에 영향 없음
6. 전송 중 새 `/cast` 명령 → 글로벌 쿨다운으로 차단

### 의존성
- Paper API 1.21.x
- OkHttp 4.x (HTTP 클라이언트)

---

## 클라이언트 모드 (minecast-client)

### 처리 로직
1. `minecast:audio` 채널 리스너 등록
2. START(`0x00`) 수신 → `ByteArrayOutputStream` 초기화, `expectedChunkCount` 저장
3. CHUNK(`0x01`) 수신 → 인덱스가 `nextExpectedIndex`와 다르면 즉시 버퍼 리셋 (재정렬 없음, 엄격한 순서)
4. END(`0x02`) 수신 → 수신 청크 수 검증 → 비동기 스레드에서 JLayer 디코딩 → OpenAL 재생
5. 재생 중 새 START 수신 시: 기존 OpenAL 소스 **stop + delete**, 새 소스 생성 후 시작
6. 게임 종료/서버 접속 해제 시: OpenAL 소스 stop + delete, 버퍼 해제 (리소스 누수 방지)

### 오디오 재생
- **OpenAL:** Minecraft가 번들하는 LWJGL의 OpenAL 바인딩 사용. 별도 OpenAL 설치 불필요.
- **MP3 디코딩:** JLayer(javazoom)를 모드에 번들하여 사용.

### 의존성
- Fabric API 1.21.x
- JLayer 1.0.1 (javazoom) — 번들 포함

---

## 에러 처리

| 상황 | 동작 |
|------|------|
| Typecast API 실패 | 관리자에게 빨간 채팅 메시지, 쿨다운 리셋 |
| 글로벌 쿨다운 중 | 관리자에게 남은 시간 안내 |
| 텍스트 길이 초과 | 관리자에게 오류 메시지 |
| 플레이어 접속 끊김 | 해당 플레이어 전송 스레드 중단 (다른 플레이어 영향 없음) |
| 청크 수 불일치 (END 시) | 버퍼 리셋, 콘솔 경고, 재생 스킵 |
| 청크 순서 오류 | 버퍼 리셋, 콘솔 경고 |
| MP3 디코딩 실패 | 재생 스킵, 콘솔 경고 |
| 전송 중 새 /cast 시도 | 글로벌 쿨다운이 차단 (전송 완료 후 쿨다운 해제) |

---

## 전송량 분석

MP3 5초 ≈ 80KB 기준:

| 동접자 수 | 1회 전송량 |
|----------|-----------|
| 10명 | ~800KB |
| 50명 | ~4MB |
| 100명 | ~8MB |

서버 메모리: MP3 한 개분(~80–160KB)만 버퍼에 유지. 글로벌 쿨다운으로 동시 전송 없음.

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
[minecast-server] (비동기)
  1. Typecast API HTTP POST → MP3 바이트 수신
  2. MP3를 32KB 청크로 분할
  3. 월드의 모든 플레이어에게 전송:
     START 패킷 → CHUNK 패킷 × N → END 패킷
    ↓ (Plugin Messaging Channel: minecast:audio)
[minecast-client] (각 플레이어)
  4. 청크 버퍼에 누적
  5. END 수신 → JLayer MP3 디코딩 → OpenAL 재생
```

---

## 패킷 프로토콜

채널: `minecast:audio`

| 패킷 타입 | 필드 | 설명 |
|----------|------|------|
| `START` | `totalBytes: int`, `chunkCount: int` | 수신 준비 |
| `CHUNK` | `index: int`, `data: byte[]` | 최대 32KB |
| `END` | (없음) | 재생 시작 신호 |

---

## 서버 플러그인 (minecast-server)

### 명령어
- `/cast <텍스트>` — 권한 노드: `minecast.use` (기본: OP)

### config.yml
```yaml
typecast:
  api-key: "YOUR_API_KEY"
  actor-id: "YOUR_ACTOR_ID"
  api-url: "https://typecast.ai/api/speak"

cast:
  cooldown-seconds: 10
  max-text-length: 200
```

### 처리 로직
1. 권한 확인, 쿨다운 확인, 텍스트 길이 확인
2. 비동기 스레드로 전환 (서버 틱 블로킹 방지)
3. Typecast HTTP POST 요청 (MP3 포맷)
4. 응답 MP3 바이트를 32KB 청크로 분할
5. 월드의 모든 온라인 플레이어에게 패킷 순차 전송

### 의존성
- Paper API 1.21.x
- OkHttp (HTTP 클라이언트)

---

## 클라이언트 모드 (minecast-client)

### 처리 로직
1. `minecast:audio` 채널 리스너 등록
2. START 수신 → `ByteArrayOutputStream` 초기화
3. CHUNK 수신 → 인덱스 순서 검증 후 버퍼 누적
4. END 수신 → 비동기 스레드에서 JLayer MP3 디코딩 → OpenAL 재생
5. 재생 중 새 START 수신 시 현재 재생 중단 후 새로 시작

### 의존성
- Fabric API 1.21.x
- JLayer (javazoom) — MP3 디코딩, 번들 포함

---

## 에러 처리

| 상황 | 동작 |
|------|------|
| Typecast API 실패 | 관리자에게 빨간 채팅 메시지 출력 |
| 쿨다운 중 재실행 | 관리자에게 남은 쿨다운 시간 안내 |
| 텍스트 길이 초과 | 관리자에게 오류 메시지 |
| 플레이어 접속 끊김 | 해당 플레이어 패킷 전송 중단 |
| 청크 순서 오류 | 클라이언트 버퍼 리셋, 콘솔 경고 |
| MP3 디코딩 실패 | 조용히 스킵, 콘솔 경고 |

---

## 전송량 분석

MP3 5초 ≈ 80KB 기준:

| 동접자 수 | 1회 전송량 |
|----------|-----------|
| 10명 | ~800KB |
| 50명 | ~4MB |
| 100명 | ~8MB |

쿨다운 10초 + 텍스트 200자 제한으로 전송량 제어.

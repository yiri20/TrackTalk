# TrackTalk

TrackTalk은 다른 음악 앱이 Android MediaSession으로 제공하는 현재 재생 정보를 읽어 Android 공식 Text-to-Speech로 곡 정보를 안내하는 네이티브 Android MVP입니다. 음악을 직접 재생하거나 스트리밍하지 않으며, Spotify API 로그인·마이크 녹음·Shazam식 분석·OCR·AccessibilityService·서버·분석 SDK를 사용하지 않습니다.

## 구현된 기능

- `MediaSessionManager`와 `MediaController` 기반 활성 세션 감지
- 재생 중 media key 세션 → 최근 재생 세션 → 최근 metadata 세션 순서의 선택
- 제목, 아티스트, 앨범, 앨범 아티스트, 트랙/디스크 번호, 전체 트랙 수, 길이, media ID, 재생 상태/위치, queue 일부 표준화
- MediaController metadata/playback/queue/활성 세션 callback 처리
- Smart, Album, 제목+아티스트, 제목만 안내 모드
- Media ID·제목·아티스트·앨범·source package 기반 fingerprint와 중복 억제
- 일시정지 후 재생/앨범 아트만 변경된 이벤트의 재안내 방지
- 지연 안내, 최소 재생 시간 이후 안내, 앱별 안내 사용 여부·모드·필드·제외 설정
- 안내 중 음악 유지·줄이기·일시정지, 곡명 안내 후 재생 옵션
- 제목의 한글/영문 자동 감지와 언어별 TTS voice 필터, 성별 표식 기반 음성 선택
- TTS 속도·높낮이·음량, 안내 중 기기 미디어 음량 임시 상승·복원
- 화면 꺼짐 및 연결 오디오 기기별 자동 활성화, 기기별 안내 사용 설정
- MediaBrowserService/미디어 버튼 지원 앱 탐색과 MediaSession 감지 앱 병합, 앱 목록 새로고침
- 앱별 설정을 음악 스트리밍, 음악·동영상, 학습·오디오북, 팟캐스트·라디오, 기타 미디어 카테고리로 자동 분류
- 선택 가능한 상단바 바로가기 알림
- Compose Material 3 홈/일반 설정/앱별 설정/음성 설정/진단 화면
- Quick Settings 안내 ON/OFF 및 음악 재생/일시정지 타일
- 일반 알림 내용은 읽거나 저장하지 않음
- Google Play Billing 기반 TrackTalk Plus 구매·구매 복원·보류 결제 상태 처리

## 수익화 초안

TrackTalk은 음악 감상 중 광고가 끼어들면 핵심 경험을 해치므로 광고보다 무료 기본 기능 + 일회성 Plus 잠금 해제를 우선합니다.

- 무료: 곡 감지, 기본 Smart 안내, 앱별 기본 활성화
- Plus: 음성 속도·높이·음량 조절, 연결 기기별 자동화, 화면 꺼짐 자동 활성화, 향후 고급 음성 기능
- Play Console 상품 ID: `tracktalk_plus_lifetime` (일회성 인앱 상품)

실제 판매 전 Play Console에 상품을 만들고 내부 테스트 트랙에서 결제 흐름을 검증해야 합니다. 현재 클라이언트는 구매 확인과 구매 복원을 처리하며, 출시 단계에서는 구매 토큰을 보안 서버에서 검증하는 절차를 추가하는 것이 안전합니다.

### 지인용 프로모션 코드

앱에 공통 비밀번호를 넣지 않고 Google Play의 일회용 프로모션 코드를 사용합니다. Play Console에서 `Monetize with Play > Promo codes`로 `tracktalk_plus_lifetime`의 코드를 만들고, 지인 한 명당 고유 코드 하나를 공유합니다. Plus 화면에 코드를 입력하면 Google Play의 공식 redeem 화면으로 이동하고, 사용 후 앱으로 돌아오면 구매 내역을 다시 조회해 Plus를 활성화합니다.

일회성 상품은 재사용 가능한 custom code가 아니라 사용자별 일회용 코드를 사용해야 합니다. 실제 코드는 Play Console에서 생성해야 하며, 상품과 프로모션을 먼저 활성화한 뒤 내부 테스트 트랙에서 redemption과 구매 복원을 확인해야 합니다.

## 설치

1. Android Studio에서 이 폴더를 엽니다.
2. Android SDK Platform 36과 Android SDK Build-Tools를 설치합니다.
3. `local.properties`의 `sdk.dir`을 개발 PC의 SDK 경로로 맞춥니다. 이 파일은 로컬 빌드 환경용입니다.
4. Gradle Sync 후 `app`의 `debug` variant를 실행하거나 다음 명령을 사용합니다.

```text
gradlew.bat :app:assembleDebug
```

생성되는 APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다.

## 원격/무선 ADB로 APK 설치

개발자 PC에서 USB 또는 Android 무선 디버깅으로 연결된 기기에 Debug APK를 설치할 수 있습니다. 일반 앱이 Android 보안 확인을 우회해 몰래 APK를 설치하는 기능은 제공하지 않으며, 이 방식은 사용자가 개발자 옵션과 무선 디버깅을 직접 허용한 개발·테스트용 흐름입니다.

기기에 무선 디버깅을 켜고 처음 한 번 페어링한 뒤 다음처럼 실행합니다.

```powershell
.\scripts\install-debug-apk.ps1 `
  -PairingAddress "192.168.0.25:37123" `
  -PairingCode "123456" `
  -WirelessAddress "192.168.0.25:42517" `
  -Launch
```

이미 페어링된 기기는 연결 주소만 지정하면 됩니다.

```powershell
.\scripts\install-debug-apk.ps1 -Device "192.168.0.25:42517" -Launch
```

USB/ADB 기기가 하나만 연결된 경우에는 `-Device`를 생략할 수 있습니다. APK가 없을 때 자동 빌드하려면 `-Build`를 추가합니다. 연결된 기기가 여러 대라면 `adb devices`로 serial을 확인한 뒤 `-Device`로 정확한 대상을 지정하세요.

## Notification Access 설정

앱 홈 화면에서 `권한 설정`을 누르거나 Android 설정에서 `알림 접근`을 엽니다. TrackTalk을 켜야 Android가 현재 재생 중인 MediaSession을 제공할 수 있습니다. 이 권한은 현재 재생 곡의 제목과 아티스트를 확인하기 위해 필요하며, 일반 알림 내용은 저장하거나 음성으로 읽지 않습니다.

## Quick Settings 타일 추가

Android 설정 또는 알림 패널의 Quick Settings 편집 화면에서 `TrackTalk`과 `음악 재생` 타일을 추가합니다. `TrackTalk`은 전역 안내 ON/OFF를 바꾸고, `음악 재생`은 현재 감지된 음악을 재생/일시정지합니다.

## 지원되는 metadata 범위

음악 앱이 MediaSession에 실제로 제공하는 값만 읽습니다. 제목, 아티스트, 앨범, 앨범 아티스트, 트랙 번호, 전체 트랙 수, 디스크 번호, 재생 길이, media ID, 재생 상태/위치, queue 설명을 지원합니다. 앱마다 제공하는 값과 callback 동작이 달라 빈 값이 있을 수 있습니다.

앨범 아트 이미지는 fingerprint에 사용하지 않으므로 아트만 바뀐 이벤트로는 다시 안내하지 않습니다.

## 한계

- Album 모드는 트랙 번호와 앨범 metadata가 안정적으로 제공될 때만 `트랙 N번, 제목`을 만들 수 있습니다. 트랙 번호가 없으면 제목만 읽습니다.
- Smart 모드는 metadata만으로 앨범 재생인지 자동 추천인지 판정한다고 주장하지 않습니다. 불확실하면 제목과 아티스트를 읽습니다.
- 화면 꺼짐 자동 활성화는 NotificationListenerService가 연결되어 있고 Android의 백그라운드 정책이 서비스를 유지하는 범위에서 동작합니다. 제조사별 배터리 최적화 정책에 따라 재연결이 필요할 수 있습니다.
- gapless playback/crossfade에는 실제 빈 구간이 없을 수 있어 “곡 사이”는 새 metadata 감지 후 0~2초 지연을 적용하는 MVP 방식입니다. 안내 중 음악 일시정지는 음악 앱이 Android TransportControls를 지원하는 범위에서 동작합니다.
- Android TTS API는 공식 성별 필드를 제공하지 않습니다. 음성 이름에 성별 표식이 있는 엔진만 남성/여성 필터가 적용되며, 나머지는 `성별 미표시 음성`으로 분류됩니다.
- 자동 언어 감지는 제목과 안내 문장의 한글 비율을 기준으로 한국어/영어를 고릅니다. 다른 문자권은 시스템 언어로 fallback합니다.
- Spotify, YouTube Music, Samsung Music 등 특정 앱을 100% 지원한다고 보장하지 않습니다. 해당 앱이 MediaSession metadata와 재생 상태를 노출하는 범위에서 동작합니다.
- MediaSession을 제공하지 않는 앱의 일반 알림 fallback 파싱은 개인정보 보호를 위해 MVP에서 구현하지 않았습니다.

## 테스트

실행한 테스트:

```text
gradlew.bat --offline :app:testDebugUnitTest
```

formatter 5개, 정책 4개, 중복 억제 5개, 혼합 언어 구간 3개, TTS 언어 fallback 1개, 활성 세션 선택 5개, 앱 카테고리 6개, Plus entitlement 2개, 프로모션 코드 2개로 총 33개 테스트가 통과합니다.

실제 Android 환경을 사용하는 MediaSession 매핑 테스트는 다음 명령으로 실행합니다.

```text
gradlew.bat :app:connectedDebugAndroidTest
```

fake MediaSession의 metadata/playback/queue 매핑, 빈 metadata, active queue fallback을 검증하는 3개 테스트가 Android 에뮬레이터와 Samsung SM-G996N에서 각각 통과했습니다(총 6건).

Android 13 이상에서는 앱을 처음 열 때 알림 권한 창을 자동으로 띄우지 않습니다. 홈 화면의 `알림 허용` 카드를 눌렀을 때만 상태 알림 권한을 요청하며, 음악 감지에 필요한 `알림 접근` 권한과는 별개입니다.

## 실제 휴대폰에서 확인

1. USB 디버깅이 켜진 Android 8.0(API 26) 이상 휴대폰을 연결합니다.
2. `gradlew.bat :app:installDebug`로 설치합니다.
3. TrackTalk을 열고 Notification Access를 허용합니다.
4. Spotify/YouTube Music/Samsung Music 등에서 곡을 재생합니다.
5. TrackTalk 홈 화면에서 감지된 앱과 곡 정보를 확인하고 TTS 테스트 버튼으로 음성 출력을 먼저 확인합니다.
6. 필요하면 외부 오디오 출력, 화면 꺼짐 자동 활성화, 앱별 모드와 지연을 조정합니다.

## 다음 단계

- 실제 음악 앱 재생 상태에서 기기별 MediaSession callback/재연결 검증
- 제조사별 배터리 최적화 예외 안내
- 필요성이 확인될 때만 제한적인 미디어 알림 metadata fallback 연구
- TTS 엔진별 voice 품질/언어 선택 UX 개선

# iTunes–YouTube 음원 매핑

## 적용 범위와 흐름

개선 계획의 우선순위 1~4를 적용한다. 국가 설정(iTunes US, YouTube KR), 검색당 후보 수 5개,
검색 API 최대 2회, 프론트 API 계약, DB 스키마와 Redis 키·TTL은 유지한다.

1. iTunes 응답의 `primaryGenreName`을 검색·메타데이터 캐시에 보존한다. 프론트 응답에는 추가하지 않는다.
2. 재생 준비는 재생 실패 캐시, 선택 캐시 순서로 확인한다. 적중 시 기존 정책을 유지한다.
3. 선택 캐시가 없으면 메타데이터의 `아티스트명 + 곡 제목`으로 검색한다. Official Audio는 붙이지 않는다.
4. YouTube 응답 순서대로 검증하여 첫 통과 후보를 선택한다.
5. 빈 결과 또는 모든 후보 탈락 시 `아티스트명 + 곡 제목 + primaryGenreName`으로 한 번 더 검색한다.
6. 장르가 null·빈 문자열·공백이면 추가 조회 없이 매핑 실패로 처리한다.
7. 2차 결과에도 같은 검증을 적용한다. 통과 후보가 없으면 `TRACK_YOUTUBE_MATCH_NOT_FOUND`를 반환한다.
8. 통과 후보만 선택 캐시에 저장한다. 실패 시 검색 결과와 메타데이터를 유지하고 재생 실패 캐시를 쓰지 않는다.

외부 API 호출 오류는 기존 `TRACK_YOUTUBE_EXTERNAL_API_ERROR`로 처리하며,
후보 불일치나 IFrame Player 재생 실패와 구분한다. 클라이언트는 호출당 HTTP 검색을 한 번만 수행하고,
Fallback 여부는 Track 서비스가 결정한다. 영상 설명과 추가 API는 사용하지 않는다.

## 후보 검증

- Video ID는 null·빈 문자열·공백이 아니어야 한다.
- 비교 문자열은 HTML Entity 디코딩 → NFKC → `Locale.ROOT` 소문자화 → 유니코드 연속 공백 축약·양끝 제거 순으로 정규화한다.
- 영상 제목에 선택 곡 제목이 포함되어야 한다. 구두점 제거, 공백 전체 제거, 제목의 버전 표기 제거는 하지 않는다.
- 아티스트 전체 이름은 영상 제목 또는 채널명에 포함되어야 하며, 앞뒤에 유니코드 문자·숫자·결합문자가 붙으면 불일치다.
- 공동 아티스트 분리, 언어별 별칭 및 소속 그룹 추론은 하지 않는다.

### 버전과 영상 유형

판별 영역은 소괄호·대괄호 내부와 양쪽에 공백이 있는 `-`, `–`, `—`, `|` 뒤 접미부이다.
정규화 후 해당 영역에서 단어 경계를 적용해 다음 표기를 찾는다.

영상의 버전 검증에는 정규화된 영상 제목에서 선택 곡 제목이 **처음 일치한 부분의 끝부터
영상 제목 끝까지**를 추가 판별 영역으로 사용한다. 모든 버전 유형에 동일하게 적용한다.
문자열을 자르지 않고 원문의 위치로 검사하여 단어 경계를 유지하며, 반복 등장하는 실제
곡 제목에 겹친 키워드도 보호한다. 기존 영역과 추가 영역에서 검출된 버전은 합집합으로 비교한다.
Full Album·Playlist의 영상 유형 검증은 기존 판별 영역만 사용한다.

| 대상 | 처리 |
| --- | --- |
| Instrumental / INST | 동일한 버전으로 취급 |
| Live / Live Clip | 동일한 라이브 버전으로 취급 |
| Cover, Remix, Sped up | 각각 별도의 버전으로 취급 |
| Full Album, Playlist | 추가 표기로 검출되면 부적합 영상으로 제외 |

선택 곡 제목 자체에서 버전 집합을 구한다. 영상의 판별 영역과 단어 경계를 그대로 유지하고, 선택 곡 제목과 일치하는 부분에
겹치는 키워드는 추가 버전·영상 유형 검사에서 제외한다. 영상 버전 집합은 선택 곡 제목에서 보존된 버전과
추가 검출 버전의 합집합이다. 선택 곡과 영상의 버전 집합이 같아야 통과한다.
실제 곡 제목의 `Cover`, `Playlist` 등의 표현만으로 제외하지 않는다.

버전 검증과 제목 검증은 모두 통과해야 한다. 선택 곡의 버전 표기가 영상에 없으면 제목
포함 검증에서도 탈락한다. `Song (Remix)`와 `Song - Remix`는 같은 버전이어도
엄격한 제목 포함 조건을 만족하지 않아 탈락한다.

Live와 Live Clip도 기존 영역과 제목 이후 추가 영역에서 검출한다. 원곡의 `(Live)`, `[Live Clip]`,
` - Live Clip`, `Song Live Clip` 후보는 제외하며, 선택한 Live 버전은 버전 집합이 일치하면 허용한다.
실제 곡 제목의 `Live`는 보호한다. `Song (Live)`와 `Song (Live Clip)`은 버전 유형이 같아도
제목 포함 조건을 만족하지 않으므로 탈락한다.

선택한 iTunes 곡 제목 자체의 버전 추출은 기존 영역만 사용한다. 구분자 없는
`Song Live`에서 버전을 추론하지 않는다. 이 제목과 같은 영상은 제목 보호로 통과할 수 있지만,
`Song Live Live Clip`처럼 추가 버전 표기가 있으면 선택 버전 집합과 달라져 탈락할 수 있다.

## 기존 데이터와 운영 영향

- 검색 `track:search:v3:*`, 메타데이터 `track:metadata:*`, 선택 `track:selection:*`,
  재생 실패 `track:playback-failure:*`의 키와 24시간 TTL을 유지한다.
- 기존 JSON의 장르 누락은 null로 읽는다. 1차 검색은 수행하되, 필요해진 Fallback은 생략한다.
- 장르 보충 외부 호출, 캐시 일괄 삭제, 자동 무효화는 하지 않는다.
- 기존 선택·재생 실패 캐시는 자연 만료까지 재사용한다. 장르 없는 검색 캐시 적중 시에도
  기존처럼 메타데이터를 저장하므로 장르 추가가 즉시 모든 캐시에 반영되지는 않는다.
- 오매핑 캐시의 개별 무효화는 현재 Video ID를 확인한 후 관리자가 별도로 대응한다.
  이번 변경에 관리자 기능이나 삭제 작업은 포함하지 않는다.
- DB의 기존 매핑은 변경하지 않는다. 매핑 실패 시 선택 캐시를 새로 만들지 않으며,
  PIN 등록의 기존 선택 캐시 필수 조건도 유지한다.

## 검증과 한계

Mock 기반 서비스 테스트로 후보 순서, Fallback 조건·동일 검증·최대 2회 호출,
장르 누락, 오류 분류와 실패 시 캐시 미변경을 확인한다. 외부 클라이언트 테스트는
요청 파라미터 및 메타데이터 역직렬화를, Redis Repository 테스트는 JSON의
장르 누락 호환성과 키·TTL을 확인한다. 검증기 테스트는 정규화·유니코드 경계·
선택 버전·복수 버전·실제 제목 보호·판별 영역 제한을 다룬다.

통과는 동일 음원임을 보장하지 않는다. 동명 아티스트·동명 곡은 통과할 수 있고,
별칭·구두점·괄호 차이로 정상 후보가 탈락할 수 있다. 정의된 영역 밖의 버전·영상 유형
표기와 Classroom Instruments, Acoustic, Slowed, Karaoke, Medley, 반복·장시간 영상은 별도 판별하지 않는다.
Fallback 증가에 따라 API 사용량이 늘 수 있다. 단위 테스트는 실제 검색 품질 QA를 대체하지 않는다.

제목 이후의 홍보 문구·아티스트명에 독립된 버전 키워드가 포함되면 정상 후보도 제외될 수 있다.
예를 들어 `Song Album Cover`, `Song Live Premiere`를 의미에 따라 예외 처리하지 않는다.
짧은 곡 제목이 앞부분에 우연히 포함되면 첫 일치 기준으로 검사 범위가 넓어진다.
괄호나 구분자 없이 곡 제목 앞에만 있는 버전 표기는 추가 영역에 포함되지 않는다.
이러한 오탐·누락을 보완하는 별칭, 문맥 추론 및 개별 음원 예외는 도입하지 않는다.

재생 시간 검증, 사용자 오매핑 신고, 오매핑 제외 캐시, DB 데이터 수정은 추후 작업이다.

## 구현 파일

Java 경로의 기준은 `src/main/java/com/example/plimap/`이며, 테스트는 동일한 구조의 `src/test/java/`에 둔다.

| 파일 | 변경 사항 |
| --- | --- |
| `global/external/itunes/dto/ItunesSearchResponse.java` | 장르 응답 필드 추가 |
| `global/external/youtube/dto/YoutubeSearchResponse.java` | 영상 제목·채널명 응답 필드 추가 |
| `global/external/youtube/YoutubeSearchClientImpl.java` | 접미사 및 내부 Fallback 제거 |
| `domain/track/dto/TrackMetadataCache.java` | nullable 장르 및 iTunes 응답 변환 추가 |
| `domain/track/dto/TrackSearchCache.java` | 장르를 보존하는 iTunes 응답 변환 추가 |
| `domain/track/service/query/impl/TrackQueryServiceImpl.java` | 외부 응답으로 내부 검색 캐시 생성 |
| `domain/track/service/command/YoutubeCandidateValidator.java` | 정규화, 제목·아티스트·버전·영상 유형 검증 추가 |
| `domain/track/service/command/impl/TrackPlaybackPreparationServiceImpl.java` | 후보 검증, 장르 Fallback 및 실패 처리 |
| `YoutubeCandidateValidatorTest.java` | 정규화·단어 경계·판별 영역·실제 제목 보호 검증 |
| `TrackPlaybackPreparationServiceImplTest.java` | 순위·Fallback·호출 수·오류·캐시 미변경 검증 |
| `TrackQueryServiceImplTest.java` | 장르 보존, 캐시 적중, 프론트 응답 유지 검증 |
| `RedisTrackMetadataCacheRepositoryTest.java` | 장르 없는 JSON과 장르 왕복 직렬화 검증 |
| `RedisTrackSearchCacheRepositoryTest.java` | 기존 검색 캐시의 장르 누락 검증 |
| `YoutubeSearchClientImplTest.java` | 1회 호출·접미사 제거·snippet 역직렬화 검증 |
| `ItunesSearchClientImplTest.java` | 기존 US 요청 조건과 장르 역직렬화 검증 |
| `docs/TRACK_MAPPING.md`, `docs/README.md` | 정책·한계·변경 파일 설명과 문서 목록 등록 |

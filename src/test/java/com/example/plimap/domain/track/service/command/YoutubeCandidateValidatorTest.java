package com.example.plimap.domain.track.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.plimap.domain.track.dto.TrackMetadataCache;
import com.example.plimap.global.external.youtube.dto.YoutubeSearchResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class YoutubeCandidateValidatorTest {

    @ParameterizedTest
    @MethodSource("titleCases")
    void 제목과_버전_및_영상_유형의_경계를_검증한다(
            String selectedTitle, String videoTitle, boolean expected
    ) {
        // given
        TrackMetadataCache metadata = metadata(selectedTitle, "Artist");
        YoutubeSearchResponse.Item candidate = candidate(videoTitle, "Artist - Topic");
        // when
        boolean result = YoutubeCandidateValidator.matches(metadata, candidate);
        // then
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> titleCases() {
        return Stream.of(
                Arguments.of("Song", "Artist - Song (Official Audio)", true),
                Arguments.of("Song", "Different title", false),
                Arguments.of("Round & Round", "Round &amp; Round", true),
                Arguments.of("Rock 'n' Roll", "Rock &#39;n&#39; Roll", true),
                Arguments.of("Song", "ＳＯＮＧ", true),
                Arguments.of("밤편지", "밤편지", true),
                Arguments.of("Two Words", "  TWO\t\nWORDS\u00a0", true),
                Arguments.of("Two Words", "TwoWords", false),
                Arguments.of("A-B", "A B", false),
                Arguments.of("Song", "Song (Instrumental)", false),
                Arguments.of("Song", "Song [INST]", false),
                Arguments.of("Song", "Song - Cover", false),
                Arguments.of("Song", "Song – Remix", false),
                Arguments.of("Song", "Song — Sped up", false),
                Arguments.of("Song", "Song | Remix", false),
                Arguments.of("Song", "Song (Producer Remix)", false),
                Arguments.of("Song", "Song [Sped   up]", false),
                Arguments.of("Song", "Song (Discover)", true),
                Arguments.of("Song", "Song (Remixes)", true),
                Arguments.of("Song", "Song (Cover2)", true),
                Arguments.of("Song", "Song (Cover\u0301)", true),
                Arguments.of("Song", "Song (한Cover)", true),
                Arguments.of("Song", "Song Cover", false),
                Arguments.of("Song", "Song-Remix", false),
                Arguments.of("Song", "Song -Remix", false),
                Arguments.of("Song", "Song- Remix", false),
                Arguments.of("Song", "Song (Live Acoustic Slowed Karaoke)", false),
                Arguments.of("Song", "Song (Classroom Instruments Acoustic Slowed Karaoke)", true),
                Arguments.of("Song (Remix)", "Artist - Song (Remix)", true),
                Arguments.of("Song [INST]", "Song [INST] (Instrumental)", true),
                Arguments.of("Song (Remix)", "Song (Remix) [Cover]", false),
                Arguments.of("Song - Remix", "Song - Remix Cover", false),
                Arguments.of("Song - Remix", "Song - Remix Playlist", false),
                Arguments.of("Song (Remix)", "Song", false),
                Arguments.of("Song (Remix)", "Song (Cover)", false),
                Arguments.of("Song (Remix)", "Song - Remix", false),
                Arguments.of("Song (Remix Cover)", "Song (Remix Cover) [Cover Remix]", true),
                Arguments.of("Song (Remix Cover)", "Song (Remix)", false),
                Arguments.of("Under Cover", "Artist - Under Cover", true),
                Arguments.of("Remix", "Artist - Remix", true),
                Arguments.of("Cover Me", "Artist - Cover Me (Official Video)", true),
                Arguments.of("Song", "Song (Full Album)", false),
                Arguments.of("Song", "Song [Playlist]", false),
                Arguments.of("Song", "Song | Full Album", false),
                Arguments.of("Song", "Song — Playlist", false),
                Arguments.of("Song", "Song Playlist", true),
                Arguments.of("Song", "Song (Playlists)", true),
                Arguments.of("Song", "Song (Full Albums)", true),
                Arguments.of("Song", "Song (Medley)", true),
                Arguments.of("My Playlist", "Artist - My Playlist", true),
                Arguments.of("Full Album", "Artist - Full Album", true),
                Arguments.of("Song (Playlist)", "Artist - Song (Playlist)", true),
                Arguments.of("My Playlist", "My Playlist [Playlist]", false),
                Arguments.of("Song", "Song [Remix] (Official Video)", false)
        );
    }

    @ParameterizedTest
    @MethodSource("liveCases")
    void 라이브_버전의_일치와_판별_영역_및_실제_제목_보호를_검증한다(
            String selectedTitle, String videoTitle, boolean expected
    ) {
        // given
        TrackMetadataCache metadata = metadata(selectedTitle, "Artist");
        // when
        boolean result = YoutubeCandidateValidator.matches(metadata, candidate(videoTitle, "Artist"));
        // then
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> liveCases() {
        return Stream.of(
                Arguments.of("Song", "Song (Live)", false),
                Arguments.of("Song", "Song [Live Clip]", false),
                Arguments.of("Song", "Song - Live Clip", false),
                Arguments.of("Song", "Song – Live", false),
                Arguments.of("Song", "Song — Live Clip", false),
                Arguments.of("Song", "Song | Live", false),
                Arguments.of("Song", "[LIVE CLIP] Song", false),
                Arguments.of("Song", "Song (Live   Clip)", false),
                Arguments.of("Song (Live)", "Song (Live)", true),
                Arguments.of("Song (Live)", "Song (Live) [Live Clip]", true),
                Arguments.of("Song (Live Clip)", "Song (Live Clip) [Live]", true),
                Arguments.of("Song (Live)", "Song", false),
                Arguments.of("Song (Live)", "Song (Live Clip)", false),
                Arguments.of("Song (Live)", "Song (Live) [Remix]", false),
                Arguments.of("Song (Live Remix)", "Song (Live Remix) [Live Clip]", true),
                Arguments.of("Live Forever", "Artist - Live Forever", true),
                Arguments.of("Live", "Artist - Live", true),
                Arguments.of("Live Clip", "Artist - Live Clip", true),
                Arguments.of("Live Forever", "Live Forever (Live Clip)", false),
                Arguments.of("Song", "Song (Alive)", true),
                Arguments.of("Song", "Song (Livestream)", true),
                Arguments.of("Song", "Song (Live2)", true),
                Arguments.of("Song", "Song (Live한)", true),
                Arguments.of("Song", "Song (한Live)", true),
                Arguments.of("Song", "Song (Live\u0301)", true),
                Arguments.of("Song", "Song Live Clip", false),
                Arguments.of("Song", "Song-Live Clip", false),
                Arguments.of("Song", "Song- Live Clip", false),
                Arguments.of("Song", "Song -Live Clip", false)
        );
    }

    @ParameterizedTest
    @MethodSource("artistCases")
    void 아티스트_전체_이름의_유니코드_경계를_제목과_채널에서_검증한다(
            String artist, String title, String channel, boolean expected
    ) {
        // given
        TrackMetadataCache metadata = metadata("Song", artist);
        // when
        boolean result = YoutubeCandidateValidator.matches(metadata, candidate(title, channel));
        // then
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> artistCases() {
        return Stream.of(
                Arguments.of("Artist", "Artist - Song", null, true),
                Arguments.of("Artist", "Song", "Artist - Topic", true),
                Arguments.of("Artist", "Song", "Other", false),
                Arguments.of("Artist", "Song", "SuperArtist", false),
                Arguments.of("Artist", "Song", "Artist2", false),
                Arguments.of("Artist", "Song", "Artist\u0301", false),
                Arguments.of("Artist", "Song", "한Artist", false),
                Arguments.of("Artist", "Song", "Artist한", false),
                Arguments.of("Artist", "Song", "[ＡＲＴＩＳＴ]", true),
                Arguments.of("A & B", "Song", "A &amp; B", true),
                Arguments.of("A & B", "Song", "A", false),
                Arguments.of("Artist Name", "Song", "ArtistName", false),
                Arguments.of("A.B", "Song", "AxB", false),
                Arguments.of("A.B", "Song", "A.B", true),
                Arguments.of("아이유", "Song", "IU", false)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 비어_있는_videoId를_제외한다(String videoId) {
        // given
        YoutubeSearchResponse.Item candidate = new YoutubeSearchResponse.Item(
                new YoutubeSearchResponse.Id(videoId), new YoutubeSearchResponse.Snippet("Song", "Artist")
        );
        // when, then
        assertThat(YoutubeCandidateValidator.matches(metadata("Song", "Artist"), candidate)).isFalse();
    }

    @Test
    void 후보와_필수_메타데이터_누락을_제외한다() {
        // given
        TrackMetadataCache metadata = metadata("Song", "Artist");
        // when, then
        assertThat(YoutubeCandidateValidator.matches(metadata, null)).isFalse();
        assertThat(YoutubeCandidateValidator.matches(metadata, new YoutubeSearchResponse.Item(null))).isFalse();
        assertThat(YoutubeCandidateValidator.matches(metadata,
                new YoutubeSearchResponse.Item(new YoutubeSearchResponse.Id("video")))).isFalse();
        assertThat(YoutubeCandidateValidator.matches(metadata, candidate(null, "Artist"))).isFalse();
        assertThat(YoutubeCandidateValidator.matches(metadata(null, "Artist"), candidate("Song", "Artist")))
                .isFalse();
        assertThat(YoutubeCandidateValidator.matches(metadata("Song", " "), candidate("Song", "Artist")))
                .isFalse();
    }

    private static TrackMetadataCache metadata(String title, String artist) {
        return new TrackMetadataCache(1L, title, artist, "Album", null, null, 1000, "Pop");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Instrumental", "INST", "Cover", "Remix", "Sped up", "Live", "Live Clip"})
    void 제목_뒤_모든_버전을_검사하고_직접_선택한_동일_버전은_허용한다(String version) {
        // given
        String selectedVersionTitle = "Song (" + version + ")";
        // when, then
        assertThat(YoutubeCandidateValidator.matches(metadata("Song", "Artist"),
                candidate("Artist 'Song' " + version, "Artist"))).isFalse();
        assertThat(YoutubeCandidateValidator.matches(metadata(selectedVersionTitle, "Artist"),
                candidate(selectedVersionTitle + " " + version, "Artist"))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("suffixCases")
    void 제목_이후_검사의_경계와_제목_보호_및_정책_한계를_검증한다(
            String selectedTitle, String videoTitle, boolean expected
    ) {
        // given
        TrackMetadataCache metadata = metadata(selectedTitle, "Artist");
        // when
        boolean result = YoutubeCandidateValidator.matches(metadata, candidate(videoTitle, "Artist"));
        // then
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> suffixCases() {
        return Stream.of(
                Arguments.of("Song", "SongLive", true),
                Arguments.of("Song", "Song Live2", true),
                Arguments.of("Song", "Song Live한", true),
                Arguments.of("Song", "Song 한Live", true),
                Arguments.of("Song", "Song Live\u0301", true),
                Arguments.of("Song", "Song Alive Livestream", true),
                Arguments.of("Song", "Live Clip Song", true),
                Arguments.of("Song", "[Live Clip] Song", false),
                Arguments.of("Song", "Song Live Clip Song", false),
                Arguments.of("Live Forever", "Live Forever / Live Forever", true),
                Arguments.of("Live Forever", "Live Forever / Live Forever Live Clip", false),
                Arguments.of("Cover", "Cover Cover", true),
                Arguments.of("Song (Live)", "Song (Live) Live Clip", true),
                Arguments.of("Song (Live Clip)", "Song (Live Clip) Live", true),
                Arguments.of("Song (INST)", "Song (INST) Instrumental", true),
                Arguments.of("Song (Live Remix)", "Song (Live Remix) Live Clip Remix", true),
                Arguments.of("Song (Live)", "Song (Live) Cover", false),
                Arguments.of("Song (Live)", "Song Live", false),
                Arguments.of("Song Live", "Song Live", true),
                Arguments.of("Song Live", "Song Live Live Clip", false),
                Arguments.of("Song", "Song Full Album Playlist", true),
                Arguments.of("Song", "Song Classroom Instruments Acoustic Slowed Karaoke", true),
                Arguments.of("Song", "Song Album Cover", false),
                Arguments.of("Song", "Song Live Premiere", false),
                Arguments.of("Song", "Song featuring Live", false),
                Arguments.of("Song", "ＳＯＮＧ &#39; LIVE\u00a0CLIP", false)
        );
    }

    @Test
    void 곡_제목_뒤_괄호_밖의_Live_Clip_오매핑_사례를_제외한다() {
        // given
        TrackMetadataCache metadata = metadata("Love wins all", "IU");
        YoutubeSearchResponse.Item candidate = candidate(
                "IU 'Love wins all' Live Clip (2024 IU HEREH WORLD TOUR CONCERT IN SEOUL)", "IU"
        );
        // when, then
        assertThat(YoutubeCandidateValidator.matches(metadata, candidate)).isFalse();
    }

    private static YoutubeSearchResponse.Item candidate(String title, String channel) {
        return new YoutubeSearchResponse.Item(
                new YoutubeSearchResponse.Id("video"), new YoutubeSearchResponse.Snippet(title, channel)
        );
    }
}

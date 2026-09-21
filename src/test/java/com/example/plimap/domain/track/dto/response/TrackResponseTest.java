package com.example.plimap.domain.track.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.plimap.global.external.itunes.dto.ItunesSearchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackResponseTest {

    @Test
    void 검색_결과의_기존_매핑_필드를_유지한다() {
        // given
        ItunesSearchResponse response = new ItunesSearchResponse(1, List.of(item()));

        // when
        TrackResponse.TrackSearchResult result = TrackResponse.TrackSearchResult.from(response);

        // then
        assertThat(result.tracks()).containsExactly(new TrackResponse.TrackSearchItem(
                123L, "밤편지", "아이유", "Palette",
                "https://image.example/cover.jpg", "https://audio.example/preview.m4a", 253000, false
        ));
    }

    private ItunesSearchResponse.Item item() {
        return new ItunesSearchResponse.Item(123L, "밤편지", "아이유", "Palette",
                "https://image.example/cover.jpg", "https://audio.example/preview.m4a", 253000);
    }

    @Test
    void iTunes_results가_null이면_빈_검색_결과로_변환한다() {
        ItunesSearchResponse response = new ItunesSearchResponse(0, null);

        TrackResponse.TrackSearchResult result =
                TrackResponse.TrackSearchResult.from(response);

        assertThat(result.tracks()).isEmpty();
    }
}

package com.example.plimap.domain.pin.converter;

import com.example.plimap.domain.pin.entity.Pin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.example.plimap.domain.pin.converter.PinConverter.parseCreatedAt;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

class PinConverterTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 내_핀_응답에_실제_공개_상태를_포함한다(boolean feedOpen) {
        // given
        Pin pin = mock(Pin.class, RETURNS_DEEP_STUBS);
        when(pin.isFeedPublic()).thenReturn(feedOpen);
        when(pin.getPinTagList()).thenReturn(List.of());
        when(pin.getCreatedAt()).thenReturn(Instant.parse("2026-07-25T12:00:00Z"));

        // when
        var response = PinConverter.toMyPin(pin);

        // then
        assertThat(response.feedOpen()).isEqualTo(feedOpen);
    }

    Instant now = Instant.parse("2026-07-25T12:00:00Z");

    @Test
    void _1분_미만이면_방금을_반환한다() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        assertThat(
                parseCreatedAt(now.minusSeconds(30), now)
        ).isEqualTo("방금");
    }

    @Test
    void _5분전을_반환한다() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        assertThat(
                parseCreatedAt(now.minus(Duration.ofMinutes(5)), now)
        ).isEqualTo("5분 전");
    }

    @Test
    void _1시간전을_반환한다() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        assertThat(
                parseCreatedAt(now.minus(Duration.ofHours(1)), now)
        ).isEqualTo("1시간 전");
    }

    @Test
    void _2일전을_반환한다() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        assertThat(
                parseCreatedAt(now.minus(Duration.ofDays(2)), now)
        ).isEqualTo("2일 전");
    }

    @Test
    void _3달전을_반환한다() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        assertThat(
                parseCreatedAt(now.minus(Duration.ofDays(90)), now)
        ).isEqualTo("3개월 전");
    }

    @Test
    void _1년전을_반환한다() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        assertThat(
                parseCreatedAt(now.minus(Duration.ofDays(365)), now)
        ).isEqualTo("1년 전");
    }

}
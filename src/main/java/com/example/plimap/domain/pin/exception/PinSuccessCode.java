package com.example.plimap.domain.pin.exception;

import com.example.plimap.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PinSuccessCode implements BaseSuccessCode {

    PIN_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "PIN_CREATED_SUCCESS",
            "핀이 생성되었습니다."
    ),

    PIN_AVAILABILITY_CHECK_SUCCESS(
            HttpStatus.OK,
            "PIN_AVAILABILITY_CHECK_SUCCESS",
            "PIN 등록 가능 여부 검증에 성공했습니다."
    ),

    PIN_UPDATE_SUCCESS(
            HttpStatus.OK,
            "PIN_UPDATE_SUCCESS",
            "PIN이 수정되었습니다."
    ),

    PIN_DELETE_SUCCESS(
            HttpStatus.OK,
            "PIN_DELETE_SUCCESS",
            "PIN이 삭제되었습니다."
    ),

    PIN_LIKE_PUT_SUCCESS(
            HttpStatus.OK,
            "PIN_LIKE_PUT_SUCCESS",
            "PIN 좋아요가 등록되었습니다."
    ),

    PIN_LIKE_DELETE_SUCCESS(
            HttpStatus.OK,
            "PIN_LIKE_DELETE_SUCCESS",
            "PIN 좋아요가 삭제되었습니다."
    ),

    MY_FEED_LIST_SEARCH_SUCCESS(
            HttpStatus.OK,
            "PIN_MY_FEED_LIST_SEARCH_SUCCESS",
            "내가 작성한 피드 목록이 조회되었습니다."
    ),

    MEMBER_FEED_LIST_SEARCH_SUCCESS(
            HttpStatus.OK,
            "PIN_MEMBER_FEED_LIST_SEARCH_SUCCESS",
            "다른 사용자가 작성한 피드 목록이 조회되었습니다."
    ),

    MY_PIN_LIST_SEARCH_SUCCESS(
        HttpStatus.OK,
        "PIN_MY_PIN_LIST_SEARCH_SUCCESS",
        "내가 작성한 핀 목록이 조회되었습니다."
    ),

    PLACE_TRACK_PIN_LIST_SEARCH_SUCCESS(
            HttpStatus.OK,
            "PIN_PLACE_TRACK_PIN_LIST_SEARCH_SUCCESS",
            "특정 장소 노래의 핀 목록이 조회되었습니다."
    ),

    PIN_SEARCH_SUCCESS(
            HttpStatus.OK,
            "PIN_SEARCH_SUCCESS",
            "핀이 조회되었습니다."
    ),

    CLUSTER_PIN_SEARCH_SUCCESS(
            HttpStatus.OK,
            "PIN_CLUSTER_PIN_SEARCH_SUCCESS",
            "viewport 기반 클러스터&핀 목록이 조회되었습니다."
    ),

    FRIENDS_RECENT_LIST_SEARCH_SUCCESS(
            HttpStatus.OK,
            "PIN_FRIENDS_RECENT_LIST_SEARCH_SUCCESS",
            "내 친구 최근 핀 목록이 조회되었습니다."
    ),

    FRIEND_FEED_TOKEN_REQUEST_SUCCESS(
            HttpStatus.OK,
            "PIN_FRIEND_FEED_TOKEN_REQUEST_SUCCESS",
            "내 친구 피드 접근 권한 요청에 성공했습니다."
    ),
    
    HOME_RECENT_PIN_LIST_SEARCH_SUCCESS(
            HttpStatus.OK,
            "PIN_HOME_RECENT_PIN_LIST_SEARCH_SUCCESS",
            "홈 화면 최신 핀 목록이 조회되었습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}

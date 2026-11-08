package com.example.plimap.domain.place.controller;

import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.place.controller.docs.PlaceControllerDocs;
import com.example.plimap.domain.place.dto.request.PlaceRequest;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.enums.PopularPlaceScope;
import com.example.plimap.domain.place.exception.PlaceSuccessCode;
import com.example.plimap.domain.place.service.command.PlaceCommandService;
import com.example.plimap.domain.place.service.query.PlaceQueryService;
import com.example.plimap.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Validated
public class PlaceController implements PlaceControllerDocs {

    private final PlaceCommandService placeCommandService;
    private final PlaceQueryService placeQueryService;

    @Override
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PlaceResponse.PopularListResult>> getPopularPlaces(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                currentMember.getMember().getId(),
                PopularPlaceScope.valueOf(scope),
                latitude,
                longitude
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_POPULAR_LIST_SUCCESS.getStatus())
                .body(ApiResponse.success(PlaceSuccessCode.PLACE_POPULAR_LIST_SUCCESS, result));
    }

    @Override
    @GetMapping("/bookmarks")
    public ResponseEntity<ApiResponse<PlaceResponse.BookmarkListResult>> getPlaceBookmarks(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        PlaceResponse.BookmarkListResult result = placeQueryService.getPlaceBookmarks(
                currentMember.getMember().getId(),
                latitude,
                longitude
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_BOOKMARK_LIST_SUCCESS.getStatus())
                .body(ApiResponse.success(PlaceSuccessCode.PLACE_BOOKMARK_LIST_SUCCESS, result));
    }

    @Override
    @GetMapping("/{placeId}")
    public ResponseEntity<ApiResponse<PlaceResponse.Detail>> getPlaceDetail(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long placeId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        PlaceResponse.Detail result = placeQueryService.getPlaceDetail(
                currentMember.getMember().getId(),
                placeId,
                latitude,
                longitude
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_DETAIL_SUCCESS.getStatus())
                .body(ApiResponse.success(PlaceSuccessCode.PLACE_DETAIL_SUCCESS, result));
    }

    @Override
    @PutMapping("/{placeId}/bookmarks")
    public ResponseEntity<ApiResponse<PlaceResponse.BookmarkResult>> bookmarkPlace(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long placeId
    ) {
        PlaceResponse.BookmarkResult result = placeCommandService.bookmarkPlace(
                currentMember.getMember().getId(),
                placeId
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_BOOKMARK_CREATE_SUCCESS.getStatus())
                .body(ApiResponse.success(
                        PlaceSuccessCode.PLACE_BOOKMARK_CREATE_SUCCESS,
                        result
                ));
    }

    @Override
    @DeleteMapping("/{placeId}/bookmarks")
    public ResponseEntity<ApiResponse<PlaceResponse.BookmarkResult>> deletePlaceBookmark(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long placeId
    ) {
        PlaceResponse.BookmarkResult result = placeCommandService.deletePlaceBookmark(
                currentMember.getMember().getId(),
                placeId
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_BOOKMARK_DELETE_SUCCESS.getStatus())
                .body(ApiResponse.success(
                        PlaceSuccessCode.PLACE_BOOKMARK_DELETE_SUCCESS,
                        result
                ));
    }

    @Override
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PlaceResponse.SearchResult>> searchPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        PlaceResponse.SearchResult result = placeQueryService.searchPlaces(
                new PlaceRequest.Search(keyword, latitude, longitude)
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_SEARCH_SUCCESS.getStatus())
                .body(ApiResponse.success(PlaceSuccessCode.PLACE_SEARCH_SUCCESS, result));
    }

    @Override
    @PostMapping("/selections")
    public ResponseEntity<ApiResponse<PlaceResponse.Selection>> selectSearchPlace(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestBody PlaceRequest.Selection request
    ) {
        PlaceResponse.Selection result = placeCommandService.selectSearchPlace(
                currentMember.getMember().getId(),
                request
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_SELECTION_SUCCESS.getStatus())
                .body(ApiResponse.success(PlaceSuccessCode.PLACE_SELECTION_SUCCESS, result));
    }

    @Override
    @GetMapping("/search-histories")
    public ResponseEntity<ApiResponse<PlaceResponse.SearchHistoryResult>> getSearchHistories(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        PlaceResponse.SearchHistoryResult result = placeQueryService.getSearchHistories(
                currentMember.getMember().getId(),
                latitude,
                longitude
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_SEARCH_HISTORY_LIST_SUCCESS.getStatus())
                .body(ApiResponse.success(
                        PlaceSuccessCode.PLACE_SEARCH_HISTORY_LIST_SUCCESS,
                        result
                ));
    }

    @Override
    @DeleteMapping("/search-histories/{historyId}")
    public ResponseEntity<ApiResponse<Void>> deleteSearchHistory(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long historyId
    ) {
        placeCommandService.deleteSearchHistory(
                currentMember.getMember().getId(),
                historyId
        );
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_SEARCH_HISTORY_DELETE_SUCCESS.getStatus())
                .body(ApiResponse.success(
                        PlaceSuccessCode.PLACE_SEARCH_HISTORY_DELETE_SUCCESS,
                        null
                ));
    }

    @Override
    @PostMapping("/map-selections")
    public ResponseEntity<ApiResponse<PlaceResponse.MapSelectionResult>> confirmMapSelection(
            @RequestBody PlaceRequest.MapSelection request
    ) {
        PlaceResponse.MapSelectionResult result = placeCommandService.confirmMapSelection(request);
        return ResponseEntity
                .status(PlaceSuccessCode.PLACE_MAP_SELECTION_SUCCESS.getStatus())
                .body(ApiResponse.success(PlaceSuccessCode.PLACE_MAP_SELECTION_SUCCESS, result));
    }
}

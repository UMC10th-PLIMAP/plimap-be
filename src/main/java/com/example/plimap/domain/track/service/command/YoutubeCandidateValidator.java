package com.example.plimap.domain.track.service.command;

import com.example.plimap.domain.track.dto.TrackMetadataCache;
import com.example.plimap.global.external.youtube.dto.YoutubeSearchResponse;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

public final class YoutubeCandidateValidator {

    private static final String WORD_CHARACTERS = "\\p{L}\\p{N}\\p{M}";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ANNOTATION = Pattern.compile(
            "\\(([^()]*)\\)|\\[([^\\[\\]]*)\\]| [-–—|] (.*)"
    );
    private static final Pattern VERSION = bounded("instrumental|inst|cover|remix|sped up|live clip|live");
    private static final Pattern VIDEO_TYPE = bounded("full album|playlist");

    private YoutubeCandidateValidator() {
    }

    public static boolean matches(TrackMetadataCache metadata, YoutubeSearchResponse.Item candidate) {
        if (candidate == null || candidate.id() == null
                || candidate.id().videoId() == null || candidate.id().videoId().isBlank()
                || candidate.snippet() == null) {
            return false;
        }

        String title = normalize(metadata.title());
        String artist = normalize(metadata.artistName());
        String videoTitle = normalize(candidate.snippet().title());
        if (title.isEmpty() || artist.isEmpty() || !videoTitle.contains(title)) {
            return false;
        }

        Pattern artistPattern = bounded(Pattern.quote(artist));
        if (!artistPattern.matcher(videoTitle).find()
                && !artistPattern.matcher(normalize(candidate.snippet().channelTitle())).find()) {
            return false;
        }

        Set<String> selectedVersions = findVersions(title, null);
        // 원래 판별 영역과 경계를 유지하면서 실제 곡 제목에 겹치는 키워드만 보호한다.
        Set<String> videoVersions = new HashSet<>(selectedVersions);
        videoVersions.addAll(findVersions(videoTitle, title));
        return selectedVersions.equals(videoVersions)
                && findKeywords(videoTitle, VIDEO_TYPE, title).isEmpty();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decoded = HtmlUtils.htmlUnescape(value);
        String normalized = Normalizer.normalize(decoded, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return WHITESPACE.matcher(normalized).replaceAll(" ").strip();
    }

    private static Set<String> findVersions(String title, String protectedTitle) {
        Set<String> versions = new HashSet<>();
        for (String version : findKeywords(title, VERSION, protectedTitle)) {
            versions.add(switch (version) {
                case "inst" -> "instrumental";
                case "live clip" -> "live";
                default -> version;
            });
        }
        return versions;
    }

    private static Set<String> findKeywords(String title, Pattern pattern, String protectedTitle) {
        Set<String> result = new HashSet<>();
        Matcher annotations = ANNOTATION.matcher(title);
        while (annotations.find()) {
            int group = annotationGroup(annotations);
            Matcher keywords = pattern.matcher(annotations.group(group));
            while (keywords.find()) {
                int start = annotations.start(group) + keywords.start();
                int end = annotations.start(group) + keywords.end();
                if (!overlapsTitle(title, protectedTitle, start, end)) {
                    result.add(keywords.group());
                }
            }
        }
        return result;
    }

    private static boolean overlapsTitle(String text, String title, int start, int end) {
        if (title == null) {
            return false;
        }
        for (int index = text.indexOf(title); index >= 0; index = text.indexOf(title, index + 1)) {
            if (start < index + title.length() && end > index) {
                return true;
            }
        }
        return false;
    }

    private static int annotationGroup(Matcher matcher) {
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
                return group;
            }
        }
        throw new IllegalStateException("판별 영역이 존재하지 않습니다.");
    }

    private static Pattern bounded(String expression) {
        return Pattern.compile("(?<![" + WORD_CHARACTERS + "])(?:" + expression
                + ")(?![" + WORD_CHARACTERS + "])");
    }
}

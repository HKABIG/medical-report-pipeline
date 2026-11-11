package com.application.backend.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Document text addressed by Unicode code point, which is how output offsets are counted.
 * Java strings index UTF-16 units, so every offset this class hands out or accepts is a code-point offset.
 */
public final class SourceText {

    private static final Pattern PAGE_MARKER = Pattern.compile("(?im)\\bpage\\s+(\\d+)\\s+of\\s+\\d+\\b");
    private static final Pattern SUBJECT_LINE = Pattern.compile("(?im)^\\s*(?:subject|re)\\s*:\\s*(.+?)\\s*$");

    private final String text;
    private final int[] codePoints;
    private final List<PageMarker> pageMarkers;

    public SourceText(String text) {
        this.text = text;
        this.codePoints = text.codePoints().toArray();
        this.pageMarkers = findPageMarkers();
    }

    public String text() {
        return text;
    }

    public int length() {
        return codePoints.length;
    }

    public int codePointAt(int index) {
        return codePoints[index];
    }

    public String substring(int start, int end) {
        return new String(codePoints, start, end - start);
    }

    /** The page an offset falls on, when the document carries "Page N of M" markers. */
    public OptionalInt pageAt(int offset) {
        if (pageMarkers.isEmpty()) {
            return OptionalInt.empty();
        }
        int page = 1;
        for (PageMarker marker : pageMarkers) {
            if (marker.offset() <= offset) {
                page = marker.page();
            }
        }
        return OptionalInt.of(page);
    }

    /** The subject or "Re:" line, else the first non-blank line. */
    public Optional<String> title() {
        Matcher subject = SUBJECT_LINE.matcher(text);
        if (subject.find()) {
            return Optional.of(subject.group(1));
        }
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst();
    }

    private List<PageMarker> findPageMarkers() {
        List<PageMarker> markers = new ArrayList<>();
        Matcher matcher = PAGE_MARKER.matcher(text);
        while (matcher.find()) {
            int offset = text.codePointCount(0, matcher.start());
            markers.add(new PageMarker(offset, Integer.parseInt(matcher.group(1))));
        }
        return List.copyOf(markers);
    }

    private record PageMarker(int offset, int page) {
    }
}

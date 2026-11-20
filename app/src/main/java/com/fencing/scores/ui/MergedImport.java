package com.fencing.scores.ui;

import com.fencing.scores.ScoresViewModel;
import com.fencing.scores.ui.MergedFragment.Row;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Fully decoded, detached rows; the caller decides whether to replace or add. */
final class MergedImport {
    final boolean bouts;
    final List<Row> rows = new ArrayList<>();

    private MergedImport(boolean bouts) {
        this.bouts = bouts;
    }

    static MergedImport decodeQr(String payload) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(payload);
        try (Reader reader = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(bytes)),
                StandardCharsets.UTF_8.newDecoder())) {
            return decode(reader, true);
        }
    }

    static MergedImport decode(Reader input, boolean qr) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        String header = reader.readLine();
        require(header != null);
        header = header.replaceFirst("^\\uFEFF", "").trim();
        MergedImport decoded;
        if (qr && header.equals("ROUND_DATA")) {
            decoded = roundQr(reader);
        } else {
            String[] cols = header.split(",", -1);
            require(cols.length >= 3 && cols[0].equals("Nr") && cols[1].equals("Name"));
            decoded = !qr && cols[2].equals("1") ? roundCsv(cols, reader) : merged(cols, reader, qr);
        }
        require(decoded.rows.stream().anyMatch(row -> !row.name.trim().isEmpty()));
        return decoded;
    }

    private static MergedImport merged(String[] header, BufferedReader reader, boolean qr) throws IOException {
        boolean hasMatches = header[2].equals("#") || header[2].equalsIgnoreCase("Matches");
        int v = hasMatches ? 3 : 2;
        require(header.length == v + 8);
        requireStatsHeader(header, v);
        require(header[v + 6].equals("Grp") &&
                (header[v + 7].equals("FinalPos") || header[v + 7].equals("Pos")));
        MergedImport result = new MergedImport(false);
        String line;
        while ((line = reader.readLine()) != null) {
            String[] c = fields(line, header.length);
            int victories = nonNegative(c[v]);
            int percent = nonNegative(c[v + 4]);
            int matches = hasMatches ? nonNegative(c[2]) : percent == 0 ? victories
                    : Math.max(victories, (int) Math.round(victories * 100.0 / percent));
            result.rows.add(new Row(nonNegative(c[0]), qr ? c[1].replace(';', ',') : c[1],
                    matches, victories, nonNegative(c[v + 1]), nonNegative(c[v + 2]), number(c[v + 3]),
                    percent, nonNegative(c[v + 5]), c[v + 6].isEmpty() ? "A" : c[v + 6],
                    c[v + 7].isEmpty() ? null : nonNegative(c[v + 7])));
        }
        return result;
    }

    private static MergedImport roundCsv(String[] header, BufferedReader reader) throws IOException {
        int n = header.length - 8;
        requireSize(n);
        requireStatsHeader(header, n + 2);
        String[] names = new String[n];
        int[][] scores = new int[n][n];
        int[] positions = new int[n];
        for (int i = 0; i < n; i++) {
            require(header[2 + i].equals(String.valueOf(i + 1)));
            String[] c = fields(reader.readLine(), header.length);
            require(number(c[0]) == i + 1);
            names[i] = c[1].trim();
            for (int j = 0; j < n; j++) {
                String score = c[2 + j];
                scores[i][j] = score.isEmpty() || (i == j && score.equalsIgnoreCase("X"))
                        ? -1 : nonNegative(score);
            }
            // Validate even the redundant stats; derive the ranking metrics from the matrix.
            for (int j = n + 2; j < n + 7; j++) {
                if (!c[j].isEmpty()) number(c[j]);
                else require(names[i].isEmpty() || j == n + 6);
            }
            positions[i] = position(c[n + 7], n);
        }
        require(reader.readLine() == null);
        return fromMatrix(names, scores, positions);
    }

    private static MergedImport roundQr(BufferedReader reader) throws IOException {
        int n = number(reader.readLine());
        requireSize(n);
        String[] names = fields(reader.readLine(), n);
        int[][] scores = new int[n][n];
        for (int i = 0; i < n; i++) {
            String[] c = fields(reader.readLine(), n);
            for (int j = 0; j < n; j++) {
                scores[i][j] = number(c[j]);
                require(scores[i][j] >= -1);
            }
        }
        int[] positions = null;
        String trailer = reader.readLine();
        if (trailer != null) {
            String[] c = fields(trailer, n + 1);
            require(c[0].equals("P") && reader.readLine() == null);
            positions = new int[n];
            for (int i = 0; i < n; i++) positions[i] = position(c[i + 1], n);
        }
        return fromMatrix(names, scores, positions);
    }

    private static MergedImport fromMatrix(String[] names, int[][] scores, int[] positions) {
        MergedImport result = new MergedImport(true);
        for (int i = 0; i < names.length; i++) {
            String name = names[i].trim();
            if (name.isEmpty()) continue;
            int wins = 0, losses = 0, matches = 0, given = 0, received = 0;
            for (int j = 0; j < names.length; j++) {
                int a = scores[i][j], b = scores[j][i];
                if (i == j || names[j].trim().isEmpty() || a < 0 || b < 0) continue;
                matches++;
                if (a > b) wins++;
                if (a < b) losses++;
                given = Math.addExact(given, a);
                received = Math.addExact(received, b);
            }
            int percent = wins + losses == 0 ? 0 : (int) Math.round(100.0 * wins / (wins + losses));
            result.rows.add(new Row(result.rows.size() + 1, name, matches, wins, given, received,
                    given - received, percent, positions == null ? 0 : positions[i], "A", null));
        }
        if (positions == null) {
            // Older Round QR exports had no P trailer. Use the Round ranking and share ties.
            List<Row> ranked = new ArrayList<>(result.rows);
            ranked.removeIf(r -> r.matches == 0);
            java.util.Comparator<Row> order = java.util.Comparator.comparingInt((Row r) -> r.percent)
                    .thenComparingInt(r -> r.index).thenComparingInt(r -> r.given).reversed();
            ranked.sort(order);
            for (int i = 0; i < ranked.size(); i++) {
                Row r = ranked.get(i);
                r.p = i > 0 && order.compare(r, ranked.get(i - 1)) == 0 ? ranked.get(i - 1).p : i + 1;
            }
        }
        return result;
    }

    private static void requireStatsHeader(String[] c, int i) {
        require(c[i].equals("V") && (c[i + 1].equals("→") || c[i + 1].equals("->"))
                && (c[i + 2].equals("←") || c[i + 2].equals("<-")) && c[i + 3].equals("I")
                && c[i + 4].equals("%") && c[i + 5].equals("P"));
    }

    private static String[] fields(String line, int count) {
        require(line != null);
        String[] c = line.split(",", -1);
        require(c.length == count);
        return c;
    }

    private static int position(String text, int n) {
        int value = text.isEmpty() ? 0 : nonNegative(text);
        require(value <= n);
        return value;
    }

    private static int number(String text) {
        require(text != null);
        return Integer.parseInt(text.trim());
    }

    private static int nonNegative(String text) {
        int value = number(text);
        require(value >= 0);
        return value;
    }

    private static void requireSize(int n) {
        require(n > 0 && n <= ScoresViewModel.MAX_PARTICIPANTS);
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid Merged or Bouts export.");
    }
}

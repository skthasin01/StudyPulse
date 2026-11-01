package studypulse.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes CSV files that open correctly in Excel: UTF-8 with BOM, comma separated,
 * CRLF line ends, header row first. Values with a comma, quote or line break are put in
 * quotes and inner quotes are doubled (RFC 4180).
 */
public final class CsvUtil {

    private static final String LINE_END = "\r\n";
    private static final char BOM = '﻿';

    private CsvUtil() {
    }

    /** One CSV field. Numbers stay as they are; text is quoted when needed. */
    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String text = value.toString();
        // Excel would run text starting with = + - @ as a formula
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /** One line without the line end. */
    public static String line(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values[i]));
        }
        return sb.toString();
    }

    public static void write(File file, String[] header, List<Object[]> rows) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(BOM);
            w.write(line((Object[]) header));
            w.write(LINE_END);
            for (Object[] row : rows) {
                w.write(line(row));
                w.write(LINE_END);
            }
        }
    }
}

package studypulse.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** CSV escaping (RFC 4180) and the Excel formula guard. */
public class CsvUtilTest {

    @Test
    public void plainTextAndNumbersStayAsTheyAre() {
        assertEquals("Lab report", CsvUtil.escape("Lab report"));
        assertEquals("120", CsvUtil.escape(120));
        assertEquals("7.5", CsvUtil.escape(7.5));
        assertEquals("", CsvUtil.escape(null));
    }

    @Test
    public void commasQuotesAndLineBreaksAreQuoted() {
        assertEquals("\"CN, Mid-term\"", CsvUtil.escape("CN, Mid-term"));
        assertEquals("\"Read \"\"Clean Code\"\" ch. 3\"", CsvUtil.escape("Read \"Clean Code\" ch. 3"));
        assertEquals("\"line 1\nline 2\"", CsvUtil.escape("line 1\nline 2"));
    }

    @Test
    public void formulaLikeTextIsNotRunByExcel() {
        assertEquals("'=SUM(A1:A2)", CsvUtil.escape("=SUM(A1:A2)"));
        assertEquals("\"'=1,2\"", CsvUtil.escape("=1,2"));
        assertEquals("-5", CsvUtil.escape(-5));   // real numbers are kept
    }

    @Test
    public void lineJoinsFieldsWithCommas() {
        assertEquals("1,\"a,b\",,3", CsvUtil.line(1, "a,b", null, 3));
    }
}

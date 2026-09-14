package com.watchalign.mobile;
import static org.junit.Assert.*;import org.junit.Test;
public class GmtReportRegressionTest {
 @Test public void unavailableIndexReportContainsNoInventedCoordinate(){String s=GmtIndexAutoAnalyzer.summarize(null,0);assertTrue(s.contains("No reliable"));assertFalse(s.matches("(?s).*[-+]?[0-9]+\\.[0-9]+ DR.*"));}
 @Test public void indexSummaryNeverOwnsTwelveOrDateThree(){String s=GmtIndexAutoAnalyzer.summarize(null,0);assertFalse(s.contains("12 marker rotation"));assertFalse(s.contains("3 marker"));}
 @Test public void triangleProductionSourceRemainsDedicated(){assertEquals("gmt.triangle12.relationship",GmtTriangle12QcModule.ID);}
}

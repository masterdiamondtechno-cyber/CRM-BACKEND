package com.company.crm.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CrmCsvTest {
  @Test
  void quotesFieldsAndEscapesQuotes() {
    assertEquals("\"said \"\"hello\"\"\"", CrmCsv.field("said \"hello\""));
  }

  @Test
  void neutralizesSpreadsheetFormulaPrefixes() {
    assertEquals("\"'=1+1\"", CrmCsv.field("=1+1"));
    assertEquals("\"' @SUM(A1)\"", CrmCsv.field(" @SUM(A1)"));
  }

  @Test
  void handlesNullAndOrdinaryValues() {
    assertEquals("\"\"", CrmCsv.field(null));
    assertEquals("\"Northstar\"", CrmCsv.field("Northstar"));
  }
}

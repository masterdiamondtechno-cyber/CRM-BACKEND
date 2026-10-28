package com.company.crm.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClientCsvImportParserTest {
    @Test
    void parsesQuotedCommasEscapedQuotesAndMultilineCells() {
        List<List<String>> rows = ClientCsvImportController.parse(
                "Company,Address,Notes\r\n\"Acme, Inc.\",\"10 Main St\",\"said \"\"hello\"\"\nnext line\"\r\n");
        assertEquals(2, rows.size());
        assertEquals("Acme, Inc.", rows.get(1).get(0));
        assertEquals("said \"hello\"\nnext line", rows.get(1).get(2));
    }
    @Test
    void recognizesExportHeaderBomAndTrailingEmptyField() {
        List<List<String>> rows = ClientCsvImportController.parse("\uFEFFCompany,Email,\r\nAcme,contact@example.com,\r\n");
        assertEquals(0, ClientCsvImportController.header(rows.get(0)).get("company"));
        assertEquals("", rows.get(1).get(2));
    }
    @Test
    void rejectsMalformedQuotedInput() {
        assertThrows(ResponseStatusException.class, () -> ClientCsvImportController.parse("Company,Notes\nAcme,\"not closed"));
        assertThrows(ResponseStatusException.class, () -> ClientCsvImportController.parse("Company,Notes\nAcme,has\"quote"));
    }
}

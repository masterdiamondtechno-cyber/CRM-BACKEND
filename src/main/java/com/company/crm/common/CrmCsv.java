package com.company.crm.common;

import java.util.regex.Pattern;

/** CSV encoding with spreadsheet formula neutralization for untrusted CRM fields. */
public final class CrmCsv {
    private static final Pattern FORMULA_PREFIX = Pattern.compile("^[\\s\\p{Cntrl}]*[=+\\-@]");
    private CrmCsv() {}

    public static String field(Object value) {
        String text = value == null ? "" : value.toString();
        if (FORMULA_PREFIX.matcher(text).find()) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}

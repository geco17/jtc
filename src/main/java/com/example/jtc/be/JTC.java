package com.example.jtc.be;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class JTC {

    private static final Logger logger = LoggerFactory.getLogger(JTC.class);

    private final ObjectMapper om;

    public JTC(ObjectMapper objectMapper) {
        this.om = objectMapper;
    }

    public Table convert(InputStream in) {
        try {
            var tree = om.readTree(in);
            var rows = new ArrayList<Row>();
            flatten(tree, startLevel(tree), null, new Row(), rows);
            return new Table(rows);
        } catch (IOException e) {
            throw new JTCException(e);
        }
    }

    private int startLevel(JsonNode tree) {
        if (tree.isObject()) {
            return 1;
        } else {
            return 0;
        }
    }

    private void flatten(JsonNode curNode, int level, String label, Row row, List<Row> rows) {
        if (!curNode.isArray()) {
            if (curNode.isObject()) {
                curNode.fields().forEachRemaining(f -> flatten(
                        f.getValue(),
                        level + 1,
                        columnLabel(level, label, f.getKey()), row, rows));
            } else {
                row.add(new Cell(label, curNode.asText()));
            }
            if (level == 1) {
                rows.add(new Row(row));
                row.clear();
            }
        } else {
            var i = new AtomicInteger(0);
            curNode.elements().forEachRemaining(el -> {
                var curIndex = i.getAndIncrement();
                flatten(
                        el,
                        level + 1,
                        columnLabel(
                                level,
                                label,
                                level == 0 ? "" : String.valueOf(curIndex)),
                        row, rows);
            });
        }
    }

    private String columnLabel(int level, String label, String key) {
        if (label == null) {
            label = "";
        }
        if (level > 1) {
            label += "/";
        }
        return label + key;
    }

    static class JTCException extends RuntimeException {
        public JTCException(Exception e) {
            super(e);
        }
    }

    private static class Cell {
        private final String label;
        private final String value;

        private Cell(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private static class Row {
        private final List<Cell> cells;

        private Row() {
            this.cells = new ArrayList<>();
        }

        private Row(Row row) {
            this.cells = new ArrayList<>(row.cells);
        }

        private void add(Cell cell) {
            cells.add(cell);
        }

        private void clear() {
            this.cells.clear();
        }
    }

    static class Col {

        private final List<Cell> cells;

        private Col() {
            this.cells = new ArrayList<>();
        }

        private void add(Cell cell) {
            this.cells.add(cell);
        }

        private String valueAt(int i) {
            var c = this.cells.get(i);
            if (c == null) {
                return null;
            }
            return c.value;
        }
    }

    public static class Table {

        private final String[] labels;

        private final String[][] data;

        private Table(List<Row> rows) {
            Set<String> labels = new TreeSet<>();
            rows.forEach(r -> r.cells.forEach(c -> labels.add(c.label)));
            Map<String, Col> cols = new HashMap<>();
            rows.forEach(r -> {
                Set<String> found = new HashSet<>();
                r.cells.forEach(c -> {
                    Col col = cols.getOrDefault(c.label, new Col());
                    col.add(c);
                    cols.put(c.label, col);
                    found.add(c.label);
                });
                Set<String> notFound = new HashSet<>(labels);
                found.forEach(notFound::remove);
                notFound.forEach(l -> {
                    Col col = cols.getOrDefault(l, new Col());
                    col.add(null);
                    cols.put(l, col);
                });
            });
            this.labels = labels.toArray(String[]::new);
            data = new String[rows.size()][labels.size()];
            for (int i = 0; i < rows.size(); i++) {
                int j = 0;
                for (String label : labels) {
                    data[i][j] = cols.get(label).valueAt(i);
                    j++;
                }
            }
        }

        /**
         * Write to an output stream and close the stream.
         *
         * @param out
         */
        public void writeCsv(OutputStream out) {
            try (var printer = new CSVPrinter(new OutputStreamWriter(out), CSVFormat.EXCEL)) {
                csvPrintWith(printer);
            } catch (IOException e) {
                throw new JTCException(e);
            }
        }

        public void writeXls(OutputStream out) {
            final var wb = new HSSFWorkbook();
            final var sheet = wb.createSheet();
            final var colNum = new AtomicInteger(0);
            final var rowNum = new AtomicInteger(0);
            final var header = sheet.createRow(rowNum.getAndIncrement());
            Arrays.stream(labels).forEach(l -> header.createCell(colNum.getAndIncrement()).setCellValue(l));
            Arrays.stream(data).forEach(dataRow -> {
                colNum.set(0);
                var excelRow = sheet.createRow(rowNum.getAndIncrement());
                Arrays.stream(dataRow).forEach(dataVal -> excelRow.createCell(colNum.getAndIncrement()).setCellValue(dataVal));
            });
            Exception ex = null;
            try {
                wb.write(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (final IOException e) {
                        ex = e;
                    }
                }
            }
            if (ex != null) {
                throw new RuntimeException(ex);
            }
        }

        /**
         * Write to a print stream, don't close it.
         *
         * @param out
         */
        public void print(PrintStream out) {
            try {
                csvPrintWith(new CSVPrinter(out, CSVFormat.EXCEL));
            } catch (IOException e) {
                throw new JTCException(e);
            }
        }

        private void csvPrintWith(CSVPrinter printer) throws IOException {
            printer.printRecord((Object[]) labels);
            for (var datum : data) {
                printer.printRecord((Object[]) datum);
            }
            printer.flush();
        }

        public String asString() {
            var baos = new ByteArrayOutputStream();
            writeCsv(baos);
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

}

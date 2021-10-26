package com.example.jtc.be;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JTC {

    private static final Logger logger = LoggerFactory.getLogger(JTC.class);

    private final ObjectMapper om;

    public JTC(ObjectMapper objectMapper) {
        this.om = objectMapper;
    }

    public Table convert(InputStream in) {
        try {
            JsonNode tree = om.readTree(in);
            List<Row> rows = new ArrayList<>();
            flatten(tree, startLevel(tree), null, new Row(), rows);
            Table table = new Table(rows);
            table.print(System.out);
            return table;
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
            AtomicInteger i = new AtomicInteger(0);
            curNode.elements().forEachRemaining(el -> {
                int curIndex = i.getAndIncrement();
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

    static class Cell {
        private final String label;
        private final String value;

        public Cell(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }
    }

    static class Row {
        private final List<Cell> cells;

        public Row() {
            this.cells = new ArrayList<>();
        }

        public Row(Row row) {
            this.cells = new ArrayList<>(row.cells);
        }

        public void add(Cell cell) {
            cells.add(cell);
        }

        public void clear() {
            this.cells.clear();
        }
    }

    static class Col {

        private final List<Cell> cells;

        public Col() {
            this.cells = new ArrayList<>();
        }

        public void add(Cell cell) {
            this.cells.add(cell);
        }

        public String valueAt(int i) {
            Cell c = this.cells.get(i);
            if (c == null) {
                return null;
            }
            return c.value;
        }
    }

    static class Table {

        private final String[] labels;

        private String[][] data;

        public Table(List<Row> rows) {
            Set<String> labels = new TreeSet<>();
            for (Row r : rows) {
                for (Cell c : r.cells) {
                    labels.add(c.label);
                }
            }
            Map<String, Col> cols = new HashMap<>();
            for (Row r : rows) {
                Set<String> found = new HashSet<>();
                for (Cell c : r.cells) {
                    Col col = cols.getOrDefault(c.label, new Col());
                    col.add(c);
                    cols.put(c.label, col);
                    found.add(c.label);
                }
                Set<String> notFound = new HashSet<>(labels);
                for (String l : found) {
                    notFound.remove(l);
                }
                for (String l : notFound) {
                    Col col = cols.getOrDefault(l, new Col());
                    col.add(null);
                    cols.put(l, col);
                }
            }
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

        public void print(PrintStream out) {
            try {
                CSVPrinter printer = new CSVPrinter(out, CSVFormat.EXCEL);
                printer.printRecord((Object[]) labels);
                for (String[] datum : data) {
                    printer.printRecord((Object[]) datum);
                }
            } catch (IOException e) {
                logger.error("Could not print!", e);
            }
        }
    }

}

package com.example.jtc.be;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
            Set<String> labels = new TreeSet<>();
            for (Row r : rows) {
                for (Cell c : r.cells) {
                    labels.add(c.label);
                }
            }
            Map<String, Col> cols = new HashMap<>();
            for (Row r :  rows) {
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
            return new Table(cols, rows.size(), labels);
        } catch (IOException e) {
            throw new JTCException(e);
        }
    }

    private int startLevel(JsonNode tree) {
        if (tree.isObject()) {
            return 1;
        }
        if (tree.isArray()) {
            Iterator<JsonNode> it = tree.elements();
            while (it.hasNext()) {
                JsonNode el = it.next();
                if (el.isObject()) {
                    return 0;
                }
            }
        }
        return -1;
    }

    private void flatten(JsonNode curNode, int level, String label, Row row, List<Row> rows) {
        if (curNode.isObject()) {
            ObjectNode node = (ObjectNode) curNode;
            node.fields().forEachRemaining(f -> flatten(
                    f.getValue(),
                    level + 1,
                    objColumnLabel(level, label, f.getKey()), row, rows));
            if (level == 1) {
                rows.add(new Row(row));
                row.clear();
            }
        } else if (curNode.isArray()) {
            ArrayNode node = (ArrayNode) curNode;
            AtomicInteger i = new AtomicInteger(0);
            node.elements().forEachRemaining(e -> flatten(
                    e,
                    level + 1,
                    arrColumnLabel(
                            level,
                            label,
                            i.getAndIncrement()), row, rows));
            // add row for array with values
            if (level == -1) {
                rows.add(new Row(row));
                row.clear();
            }
        } else {
            row.add(new Cell(label, curNode.asText()));
        }
    }

    private String objColumnLabel(int level, String label, String key) {
        if (label == null) {
            label = "";
        }
        if (level > 1) {
            label += "/";
        }
        return label + key;
    }

    private String arrColumnLabel(int level, String label, int index) {
        if (label == null) {
            label = "";
        }
        if (level == -1) {
            label += index;
        }
        if (level > 1) {
            label += "/" + index;
        }
        return label;
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

        // TODO
        public Table(Map<String, Col> colData, int size, Set<String> labels) {
            for (String label : labels) {
                System.out.print(label + ";");
            }
            System.out.println();
            for (int i = 0; i < size; i++) {
                for (String label : labels) {
                    System.out.print(colData.get(label).valueAt(i) + ";");
                }
                System.out.println();
            }
        }
    }

}

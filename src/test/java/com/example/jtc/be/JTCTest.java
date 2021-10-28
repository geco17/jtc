package com.example.jtc.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class JTCTest {

    private final JTC jtc = new JTC(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void testConversion(int number) {
        try (var in = getClass().getResourceAsStream("/" + number + ".json");
             var exp = getClass().getResourceAsStream("/" + number + ".csv")) {
            var table = jtc.convert(in);
            var actual = table.asString();
            var expected = new String(exp.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(actual).isEqualTo(expected);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void testExcel(int number) {
        try (var in = getClass().getResourceAsStream("/" + number + ".json");
             var exp = getClass().getResourceAsStream("/" + number + ".csv")) {
            var table = jtc.convert(in);
            var baos = new ByteArrayOutputStream();
            table.writeXls(baos);
            var wb = new HSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()));
            var sheet = wb.getSheetAt(0);
            var csvBaos = new ByteArrayOutputStream();
            CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(csvBaos), CSVFormat.EXCEL);
            sheet.rowIterator().forEachRemaining(row -> {
                try {
                    var dRow = new ArrayList<String>();
                    row.cellIterator().forEachRemaining(c -> dRow.add(c.getRichStringCellValue().getString()));
                    printer.printRecord(dRow);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            printer.flush();
            csvBaos.close();
            var actual = csvBaos.toString(StandardCharsets.UTF_8);
            var expected = new String(exp.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(actual).isEqualTo(expected);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

package com.example.demo.system.application;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Java 业务数据归档服务。
 *
 * <p>归档内容仅包含 Java 持有的关系型数据库数据以及业务文件目录。Agent Memory、Embedding、
 * Qdrant 等数据由 Python Agent Engine 独立管理，不再进入 Java 备份。</p>
 */
@Slf4j
@Service
public class DataArchiveService {

    private static final String BACKUP_JSON = "backup.json";
    private static final String FILES_PREFIX = "files/";
    private static final List<String> MANAGED_DIRS = List.of("data", "generated");

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public DataArchiveService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Java 业务数据库和托管文件目录写入 ZIP。
     *
     * @param outputStream ZIP 输出流。
     */
    public void writeArchiveTo(OutputStream outputStream) {
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeDatabasePayload(zipOutputStream);
            int fileCount = appendManagedDirectories(zipOutputStream);
            zipOutputStream.finish();
            log.info("Java 业务数据导出完成: files={}", fileCount);
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("导出 Java 业务数据失败: " + error.getMessage(), error);
        }
    }

    private void writeDatabasePayload(ZipOutputStream zipOutputStream)
            throws IOException, SQLException {
        zipOutputStream.putNextEntry(new ZipEntry(BACKUP_JSON));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zipOutputStream);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String quote = safeQuote(metadata.getIdentifierQuoteString());
            generator.writeStartObject();
            generator.writeStringField("version", "5.0-java-business-only");
            generator.writeStringField("exportedAt", LocalDateTime.now().toString());
            generator.writeStringField("databaseProduct", metadata.getDatabaseProductName());
            generator.writeArrayFieldStart("managedDirectories");
            for (String directory : MANAGED_DIRS) {
                generator.writeString(directory);
            }
            generator.writeEndArray();
            generator.writeObjectFieldStart("tables");
            for (String table : listCurrentDatabaseTables(connection, metadata)) {
                generator.writeFieldName(table);
                writeTableRows(generator, connection, table, quote);
            }
            generator.writeEndObject();
            generator.writeEndObject();
            generator.flush();
        }
        zipOutputStream.closeEntry();
    }

    private List<String> listCurrentDatabaseTables(
            Connection connection,
            DatabaseMetaData metadata) throws SQLException {
        List<String> tables = new ArrayList<>();
        String catalog = connection.getCatalog();
        try (ResultSet resultSet = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String table = resultSet.getString("TABLE_NAME");
                if (table != null && isSafeIdentifier(table)) {
                    tables.add(table);
                }
            }
        }
        tables.sort(String.CASE_INSENSITIVE_ORDER);
        return tables;
    }

    private void writeTableRows(
            JsonGenerator generator,
            Connection connection,
            String table,
            String quote) throws SQLException, IOException {
        String sql = "SELECT * FROM " + quoteIdentifier(table, quote);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            generator.writeStartArray();
            while (resultSet.next()) {
                generator.writeStartObject();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    generator.writeFieldName(metadata.getColumnName(index));
                    generator.writeObject(normalizeCellValue(resultSet.getObject(index)));
                }
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
    }

    private int appendManagedDirectories(ZipOutputStream zipOutputStream) throws IOException {
        int count = 0;
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        for (String directory : MANAGED_DIRS) {
            Path root = projectRoot.resolve(directory).normalize();
            if (!root.startsWith(projectRoot) || !Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String relative = projectRoot.relativize(file).toString().replace('\\', '/');
                    zipOutputStream.putNextEntry(new ZipEntry(FILES_PREFIX + relative));
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                    count++;
                }
            }
        }
        return count;
    }

    private Object normalizeCellValue(Object value) {
        if (value == null
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof String) {
            return value;
        }
        if (value instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }

    private String quoteIdentifier(String identifier, String quote) {
        if (!isSafeIdentifier(identifier)) {
            throw new IllegalArgumentException("非法数据库标识符: " + identifier);
        }
        return quote + identifier + quote;
    }

    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && identifier.matches("[A-Za-z0-9_]+")
                && !identifier.toLowerCase(Locale.ROOT).startsWith("information_schema");
    }

    private String safeQuote(String quote) {
        return quote == null || quote.isBlank() ? "`" : quote.trim();
    }
}

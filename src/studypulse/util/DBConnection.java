package studypulse.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens SQLite connections to data/studypulse.db and creates the schema.
 * The data folder sits next to the project (when run from NetBeans) or
 * next to StudyPulse.jar (when run from dist/).
 */
public final class DBConnection {

    private static final String DATA_FOLDER = "data";
    private static final String DB_FILE_NAME = "studypulse.db";
    private static final String SCHEMA_RESOURCE = "/studypulse/resources/schema.sql";

    private static File databaseFile;

    private DBConnection() {
    }

    /** Returns a new connection with foreign keys switched on. Close it with try-with-resources. */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver not found. Is lib/sqlite-jdbc on the classpath?", ex);
        }
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDatabaseFile().getAbsolutePath());
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA foreign_keys = ON")) {
            ps.execute();
        } catch (SQLException ex) {
            conn.close();
            throw ex;
        }
        return conn;
    }

    /** Creates the data folder and runs schema.sql (CREATE ... IF NOT EXISTS). */
    public static void initDatabase() throws SQLException, IOException {
        List<String> statements = readSchemaStatements();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (String sql : statements) {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.execute();
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Tests only: use another database file (e.g. a temporary one) instead of data/studypulse.db. */
    public static synchronized void useDatabaseFile(File file) {
        databaseFile = file;
    }

    /** The data folder (data/studypulse.db and data/error.log live here). */
    public static File getDataFolder() {
        return getDatabaseFile().getParentFile();
    }

    public static synchronized File getDatabaseFile() {
        if (databaseFile == null) {
            File dataDir = new File(findBaseDirectory(), DATA_FOLDER);
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            databaseFile = new File(dataDir, DB_FILE_NAME);
        }
        return databaseFile;
    }

    /** Folder that contains StudyPulse.jar, or the working directory (project root) otherwise. */
    private static File findBaseDirectory() {
        try {
            File location = new File(DBConnection.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (location.isFile() && location.getName().toLowerCase().endsWith(".jar")) {
                return location.getParentFile();
            }
        } catch (Exception ex) {
            // fall back to the working directory
        }
        return new File(System.getProperty("user.dir"));
    }

    private static List<String> readSchemaStatements() throws IOException {
        InputStream in = DBConnection.class.getResourceAsStream(SCHEMA_RESOURCE);
        if (in == null) {
            throw new IOException("Schema file not found: " + SCHEMA_RESOURCE);
        }
        StringBuilder sql = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().startsWith("--")) {
                    sql.append(line).append('\n');
                }
            }
        }
        List<String> statements = new ArrayList<String>();
        for (String part : sql.toString().split(";")) {
            if (part.trim().length() > 0) {
                statements.add(part.trim());
            }
        }
        return statements;
    }
}

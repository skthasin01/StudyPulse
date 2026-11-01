package studypulse.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import studypulse.dao.SchemaDAO;
import studypulse.util.DBConnection;

/** Database start-up and the schema check printed by Main. */
public class DatabaseService {

    private final SchemaDAO schemaDAO = new SchemaDAO();

    /** Creates data/studypulse.db and all tables if they do not exist yet. */
    public void initialize() throws SQLException, IOException {
        DBConnection.initDatabase();
    }

    public File getDatabaseFile() {
        return DBConnection.getDatabaseFile();
    }

    /**
     * Checks the ID rule: the first column of every table is id INTEGER PRIMARY KEY AUTOINCREMENT,
     * and every user_id / task_id column has an index. Returns the console lines; problems are
     * listed after the two summary lines.
     */
    public List<String> checkSchema() throws SQLException {
        List<String> problems = new ArrayList<String>();
        List<String> tables = schemaDAO.findTableNames();
        int idOk = 0;
        int fkColumns = 0;
        int fkIndexed = 0;
        for (String table : tables) {
            List<String[]> columns = schemaDAO.findColumns(table);
            String[] first = columns.isEmpty() ? null : columns.get(0);
            boolean ok = first != null && "id".equals(first[0]) && "INTEGER".equalsIgnoreCase(first[1])
                    && "1".equals(first[2]) && schemaDAO.findCreateSql(table).toUpperCase().contains("AUTOINCREMENT");
            if (ok) {
                idOk++;
            } else {
                problems.add("  " + table + ": first column is not id INTEGER PRIMARY KEY AUTOINCREMENT");
            }
            Set<String> indexed = schemaDAO.findIndexedColumns(table);
            for (String[] c : columns) {
                if ("user_id".equals(c[0]) || "task_id".equals(c[0])) {
                    fkColumns++;
                    if (indexed.contains(c[0])) {
                        fkIndexed++;
                    } else {
                        problems.add("  " + table + "." + c[0] + " has no index");
                    }
                }
            }
        }
        List<String> lines = new ArrayList<String>();
        lines.add("Schema check: " + idOk + "/" + tables.size() + " tables use id INTEGER PRIMARY KEY AUTOINCREMENT");
        lines.add("Index check: " + fkIndexed + "/" + fkColumns + " user_id / task_id columns have an index");
        lines.addAll(problems);
        return lines;
    }
}

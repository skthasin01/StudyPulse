package studypulse.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import studypulse.util.DBConnection;

/**
 * Reads the database structure from sqlite_master and the PRAGMA table functions
 * (pragma_table_info, pragma_index_list, pragma_index_info), for the startup schema check.
 */
public class SchemaDAO {

    /** Application tables in creation order. */
    public List<String> findTableNames() throws SQLException {
        List<String> names = new ArrayList<String>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT name FROM sqlite_master "
                        + "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY rowid");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** The CREATE TABLE statement of a table. */
    public String findCreateSql(String table) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    /** Columns in order: {name, declared type, pk position} (PRAGMA table_info). */
    public List<String[]> findColumns(String table) throws SQLException {
        List<String[]> columns = new ArrayList<String[]>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT name, type, pk FROM pragma_table_info(?) ORDER BY cid")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }
            }
        }
        return columns;
    }

    /** Columns that are the first column of some index of the table (can be used for lookups). */
    public Set<String> findIndexedColumns(String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<String>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT ii.name FROM pragma_index_list(?) il, pragma_index_info(il.name) ii "
                        + "WHERE ii.seqno = 0")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }
}

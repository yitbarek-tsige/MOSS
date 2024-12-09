/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.moha.moss;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 *
 * @author Yitbarek
 */
public class DatabaseService {


    public Connection getConnection() throws Exception {
        Properties config = Supporter.loadConfig();
        String url = config.getProperty("database.url");
        String username = config.getProperty("database.username");
        String password = config.getProperty("database.password");
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, username, password);
    }


    public List<String> userDetail(String username) {
        String sql = "SELECT EmpRole, EmpDep FROM login WHERE EmpUserName = ?";
        List<String> details = new ArrayList<>();
        try (Connection conn = getConnection()) {
            PreparedStatement pst = conn.prepareStatement(sql);
            {
                pst.setString(1, username);
                ResultSet rs = pst.executeQuery();
                while (rs.next()) {
                    String role = rs.getString("EmpRole");
                    String department = rs.getString("EmpDep");
                    details.add(role);
                    details.add(department);
                }
            }
            return details;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e);
        }
        return details;
    }


    public boolean signup(String department, String username, String password) throws Exception {
        String query = "INSERT INTO login (EmpUserName, EmpPass, EmpRole, EmpDep) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, "user");
            pstmt.setString(4, department);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    public boolean signupMessages(String department, String username) throws Exception {
        String query = "SELECT MessageID FROM messages WHERE MessageType = 'broadcast' AND ToDep = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, department);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int MessageID = rs.getInt("MessageID");
                    String sql3 = "INSERT INTO usermessage (user_id, message_id, is_read) VALUES (?, ?, ?)";

                    try (PreparedStatement pst1 = conn.prepareStatement(sql3)) {
                        pst1.setString(1, username);
                        pst1.setInt(2, MessageID);
                        pst1.setBoolean(3, false);
                        pst1.executeUpdate();
                    }
                }
                return true; // Return true if any messages were inserted
            }
        } catch (SQLException ex) {
            ex.printStackTrace(); // Log the exception for debugging
            return false; // Return false on exception
        }
    }


    public void saveMessage(String file_name, String MessType, String sender, String recipient, String message) throws Exception {
        List<String> usernames = new ArrayList<>();
        String sql0 = "SELECT EmpUserName FROM login WHERE EmpDep = ?";
        try (Connection conn = getConnection();
             PreparedStatement pst0 = conn.prepareStatement(sql0)) {
            pst0.setString(1, recipient);
            ResultSet resultSet = pst0.executeQuery();
            while (resultSet.next()) {
                String userName = resultSet.getString("EmpUserName");
                usernames.add(userName);
            }
        }
        String sql = "INSERT INTO messages (file_name, MessageType, toDep, EmpUserName, note, timestamp ) VALUES (?, ?, ?, ?, ?, NOW())";
        int messageId = -1;
        try (Connection conn = getConnection();
             PreparedStatement pst = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, file_name);
            pst.setString(2, MessType);
            pst.setString(3, recipient);
            pst.setString(4, sender);
            pst.setString(5, message);
            pst.executeUpdate();
            ResultSet generatedKeys = pst.getGeneratedKeys();
            if (generatedKeys.next()) {
                messageId = generatedKeys.getInt(1);
            }
            if (messageId != -1) {
                String sql3 = "INSERT INTO usermessage (user_id, message_id, is_read) VALUES (?, ?, ?)";
                try (PreparedStatement pst1 = conn.prepareStatement(sql3)) {
                    if (!usernames.contains(sender)) {
                        pst1.setString(1, sender);
                        pst1.setInt(2, messageId);
                        pst1.setBoolean(3, true);
                        pst1.addBatch();
                    }
                    for (String x : usernames) {
                        pst1.setString(1, x);
                        pst1.setInt(2, messageId);
                        pst1.setBoolean(3, x.equals(sender));
                        pst1.addBatch();
                    }
                    pst1.executeBatch();
                } catch (SQLException e) {
                    e.printStackTrace(); // Log the error
                }
            }
        }
    }


    public void markMessagesAsRead(int messageid, String username) throws SQLException, Exception {
        String sql = "UPDATE usermessage SET is_read = true, read_at = NOW() WHERE message_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, messageid);
            pst.setString(2, username);
            pst.executeUpdate();
        }
    }


    public List<Message> fetchMessages(String role, String username, String department) throws Exception {
        List<Message> messages = new ArrayList<>();
        String sql;
        if (role.equals("user")) {
            sql = """
                    SELECT m.MessageID, m.file_name, m.ToDep, m.EmpUserName, m.timestamp, m.note, u.is_read 
                    FROM messages m
                    LEFT JOIN usermessage u ON m.MessageID = u.message_id AND u.user_id = ?
                    WHERE (m.MessageType = 'broadcast' AND m.ToDep = ?)
                    OR (m.MessageType='request' AND m.EmpUserName = ?)
                    OR (m.EmpUserName = ?)
                    """;
        } else if (role.equals("head")) {
            sql = """
                    SELECT m.MessageID, m.file_name, m.ToDep, m.EmpUserName, m.timestamp, m.note, u.is_read 
                    FROM messages m
                    LEFT JOIN usermessage u ON m.MessageID = u.message_id AND u.user_id = ?
                    WHERE (m.MessageType IN ('Request', 'broadcast')) 
                    AND (m.ToDep = ? OR m.EmpUserName = ?)
                    """;
        } else {
            return messages;
        }

        try (Connection conn = getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            if (role.equals("user")) {
                pst.setString(1, username);
                pst.setString(2, department);
                pst.setString(3, username);
                pst.setString(4, username);
            } else if (role.equals("head")) {
                pst.setString(1, username);
                pst.setString(2, department);
                pst.setString(3, username);
            }
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                int messageId = rs.getInt("MessageID");
                String from = rs.getString("EmpUserName");
                String file = rs.getString("file_name");
                String toDept = rs.getString("ToDep");
                String content = rs.getString("note");
                boolean isRead = rs.getBoolean("is_read");
                String timestamp = rs.getString("timestamp");

                messages.add(new Message(messageId, from, content, isRead, timestamp, toDept, file));
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return messages;
    }

    public String DeptFetcher(String username) {
        String query = "SELECT EmpDep FROM login WHERE EmpUserName = ?";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(query)) {

            preparedStatement.setString(1, username);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
               return resultSet.getString("EmpDep");
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean sendRequest(String file_path, String username, String department, String note, String messType) throws Exception {
        List<String> usernames = new ArrayList<>();
        String sql0 = "SELECT EmpUserName FROM login WHERE EmpUserName = ? OR EmpDep = ? AND (EmpRole = 'head')";
        try (Connection conn = getConnection();
             PreparedStatement pst0 = conn.prepareStatement(sql0)) {
            pst0.setString(1, username);
            pst0.setString(2, department);
            ResultSet resultSet = pst0.executeQuery();
            while (resultSet.next()) {
                String userName = resultSet.getString("EmpUserName");
                usernames.add(userName);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }

        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO messages (EmpUserName, ToDep, note, file_name, MessageType, timestamp ) VALUES (?, ?, ?, ?, ?, NOW())";
            int messageId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, username);
                stmt.setString(2, department);
                stmt.setString(3, note);
                stmt.setString(4, file_path);
                stmt.setString(5, messType);
                stmt.executeUpdate();  // Returns true if insertion is successful

                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    messageId = generatedKeys.getInt(1);
                }
                if (messageId != -1) {
                    String sql3 = "INSERT INTO usermessage (user_id, message_id, is_read) VALUES (?, ?, ?)";
                    try (PreparedStatement pst1 = conn.prepareStatement(sql3)) {
                        for (String x : usernames) {
                            pst1.setString(1, x);
                            pst1.setInt(2, messageId);
                            pst1.setBoolean(3, x.equals(username));
                            pst1.addBatch();
                        }
                        pst1.executeBatch();
                    }
                }
                } catch(SQLException ex){
                    ex.printStackTrace();
                    return false;
                }
                return true;
            }
        }
    }

  

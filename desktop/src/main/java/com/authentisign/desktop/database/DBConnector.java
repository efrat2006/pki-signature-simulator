package com.authentisign.desktop.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnector {

    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=SigningAppDB;encrypt=true;trustServerCertificate=true;";
    private static final String USER = "sa";
    private static final String PASS = "123456";

    public static Connection getConnection() throws SQLException, ClassNotFoundException {
        try{
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            Connection conn = DriverManager.getConnection(URL,USER,PASS);
            System.out.println("Connected to SQL SERVER successfully");
            return conn;
        }
        catch(ClassNotFoundException e){
            System.out.println("הדרייבר לא נמצא" + e.getMessage());
            return null;
        }
        catch(SQLException e){
            System.out.println("שגיאת חיבור" + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        System.out.println("מנסה להתחבר ל-SQL Server");
        try {
            Connection testConn = getConnection();

            if (testConn != null) {
                System.out.println("החיבור הצליח, הדרייבר תקין והמשתמש sa מאושר");
                try {
                    testConn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("החיבור נכשל");
            }
        }
        catch (Exception e) {
        }
    }



}

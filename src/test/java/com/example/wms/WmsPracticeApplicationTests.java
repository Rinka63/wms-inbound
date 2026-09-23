package com.example.wms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest
class WmsPracticeApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void jdbcConnectionTest() throws SQLException {
        try(Connection conn = dataSource.getConnection();
            PreparedStatement statement =
                    conn.prepareStatement("SELECT 1, DATABASE()");
            ResultSet rs = statement.executeQuery()){


            assertTrue(conn.isValid(2));

            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertEquals("wms_warehouse", rs.getString(2));

            System.out.println("JDBC connection successful");
            System.out.println("Database: " + rs.getString(2));
        }
    }

}

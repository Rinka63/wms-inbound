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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应用启动与基础设施集成测试
 */
@SpringBootTest
class WmsPracticeApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        assertNotNull(dataSource, "数据源应成功加载");
    }

    @Test
    void jdbcConnectionTest() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement statement = conn.prepareStatement("SELECT 1, DATABASE()");
                     ResultSet rs = statement.executeQuery()) {

            assertTrue(conn.isValid(2));

            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertEquals("wms_warehouse", rs.getString(2));
        }
    }
}

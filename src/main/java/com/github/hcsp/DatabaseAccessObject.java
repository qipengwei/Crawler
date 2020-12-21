package com.github.hcsp;

import java.io.File;
import java.sql.*;

class JdbcCrawlerDao implements CrawlerDao{
    //从数据库加载即将处理链接的代码
    File projectDir = new File(System.getProperty("basedir", System.getProperty("user.dir")));
    String jdbcUrl = "jdbc:h2:file:" + new File(projectDir, "target/mall").getAbsolutePath();
    Connection connection;

    final String NotTable = "LINKS_TO_BE_PROCESSED";
    final String UsedTable = "LINKS_ALREADY_PROCESSED";

    JdbcCrawlerDao() {
        try {
            connection = DriverManager.getConnection(jdbcUrl);
        } catch (SQLException throwables) {
                throw new RuntimeException(throwables);
        }
    }

    /**
     * 从未爬取数据库中拿出一个连接
     * @return 返回本次爬取的链接
     */
    public String queryNextUnusedUrl() throws SQLException {
        String queryUrl = null;
        ResultSet resultSet = null;
        try {
            //sql语句
            PreparedStatement statement = connection.prepareStatement("select * from LINKS_TO_BE_PROCESSED limit 1");
            //拿到结果集
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                queryUrl = resultSet.getString(1);
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return queryUrl;
    }

    /**
     * 更新数据库链接操作 delete/insert
     *
     * @param link 要删除的链接
     * @return 返回数据库影响行数 0则操作失败
     */
    public int updateLinks(String link) throws SQLException {
        int resultSet;
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement("delete from LINKS_TO_BE_PROCESSED where link = ?");
            statement.setString(1, link);
            //executeUpdate 方法返回影响数据库行数 返回0 则更新失败
            resultSet = statement.executeUpdate();
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }
        }

        return resultSet;
    }

    /**
     * 查询链接是否已经使用过 / 查询已使用链接库
     * @param link 要查询的链接
     * @return true存在 false反之
     */
    public boolean queryLinkUsed(String link) throws SQLException {
        ResultSet resultSet = null;
        boolean exist = false;
        try {
            PreparedStatement statement = connection.prepareStatement("select link from LINKS_ALREADY_PROCESSED where link = ?");
            statement.setString(1, link);
            //executeUpdate 方法返回影响数据库行数 返回0 则更新失败
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                exist = true;
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return exist;
    }

    /**
     * 消费本次爬取的链接
     * @return 返回本次消费的链接
     */
    public String ConsumptionUnusedUrl() {
        String link = null;
        try {
            //从需要爬取数据库 拿出 本次使用的链
            link = queryNextUnusedUrl();
            //拿出本次爬取链接从原数据库删除
            if (link != null) {
                updateLinks(link);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return link;
    }

    /**
     * 写入爬取链接
     * @param link 要写入的链接
     * @param tableName 写入链接的表名`
     * @return 返回影响表结构数量 0 则插入失败
     */
    public int WriteCrawlOrigin(String link, String tableName) {
        PreparedStatement statement = null;
        int change = 0;
        String sql = String.format("insert into %s (link) values(?)", tableName);
        try {
            statement = connection.prepareStatement(sql);
            statement.setString(1, link);
            change = statement.executeUpdate();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }
        }

        return change;
    }

    /**
     * 写入未爬取的链接
     * @param link 爬取的链接
     */
    public int WriteNotCrawl(String link) {
        return WriteCrawlOrigin(link, NotTable);
    }

    /**
     * 写入已爬取链接
     * @param link
     * @return 返回更新影响行数量
     */
    public int WriteUseCrawl(String link) {
        return WriteCrawlOrigin(link, UsedTable);
    }

    /**
     * 插入新闻实体类
     * @param title 新闻标题
     * @param content 新闻内容
     * @param link 新闻链接
     * @throws SQLException sql异常
     * @return 返回数据库影响行数 0 则操作失败
     */
    public int WriteToDatabaseNews(String title, String content, String link) throws SQLException {
        int resultSet;
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement("insert into NEWS (TITLE, CONTENT, URL, CREATED_AT, UPDATE_AT) VALUES (?, ?, ?, now(), now())");
            statement.setString(1, title);
            statement.setString(2, content);
            statement.setString(3, link);
            //executeUpdate 方法返回影响数据库行数 返回0 则更新失败
            resultSet = statement.executeUpdate();
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }
        }

        return resultSet;
    }


}

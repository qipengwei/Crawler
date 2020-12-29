package com.github.hcsp;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MyBatisCrawlerDao implements CrawlerDao{
    final String NotTable = "LINKS_TO_BE_PROCESSED";
    final String UsedTable = "LINKS_ALREADY_PROCESSED";
    String resource = "db/mybatis/config.xml";
    InputStream inputStream;
    SqlSessionFactory sqlSessionFactory;

    MyBatisCrawlerDao() {
        try {
            inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean queryLinkUsed(String link) throws SQLException {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int row = session.selectOne("com.github.hcsp.Crawler.contLink", link);
            return row > 0;
        }
    }

    /**
     * 方法中含有两段操作  所以为了一致性把方法变成原子操作
     * 消费本次爬取的url
     * @return 返回本次要消费的url
     */
    @Override
    public synchronized String ConsumptionUnusedUrl() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            String link = session.selectOne("com.github.hcsp.Crawler.selectNextLink");
            if (link != null) {
                session.delete("com.github.hcsp.Crawler.hardDelete", link);
            }
            return link;
        }

    }

    @Override
    public int WriteCrawlOrigin(String link, String tableName) {
        Map<String, Object> param = new HashMap<>();
        param.put("tableName", tableName);
        param.put("link", link);
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.insert("com.github.hcsp.Crawler.insertLink", param);
        }
    }

    @Override
    public int WriteToDatabaseNews(String title, String content, String link) throws SQLException {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.insert("com.github.hcsp.Crawler.insertNews",
                    new News(title, content, link));
        }
    }

    @Override
    public int WriteNotCrawl(String link) {
        return this.WriteCrawlOrigin(link, NotTable);
    }

    @Override
    public int WriteUseCrawl(String link) {
        return this.WriteCrawlOrigin(link, UsedTable);
    }
}

package com.github.hcsp;

import java.sql.SQLException;

public class MyBatisCrawlerDao implements CrawlerDao{
    @Override
    public int updateLinks(String link) throws SQLException {
        return 0;
    }

    @Override
    public boolean queryLinkUsed(String link) throws SQLException {
        return false;
    }

    @Override
    public String ConsumptionUnusedUrl() {
        return null;
    }

    @Override
    public int WriteCrawlOrigin(String link, String tableName) {
        return 0;
    }

    @Override
    public int WriteToDatabaseNews(String title, String content, String link) throws SQLException {
        return 0;
    }

    @Override
    public int WriteNotCrawl(String link) {
        return 0;
    }

    @Override
    public int WriteUseCrawl(String link) {
        return 0;
    }
}

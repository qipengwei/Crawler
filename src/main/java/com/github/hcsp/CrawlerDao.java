package com.github.hcsp;

import java.sql.SQLException;

public interface CrawlerDao {
    int updateLinks(String link) throws SQLException;

    boolean queryLinkUsed(String link) throws SQLException;

    String ConsumptionUnusedUrl();

    int WriteCrawlOrigin(String link, String tableName);

    int WriteToDatabaseNews(String title, String content, String link) throws SQLException;

    int WriteNotCrawl(String link);

    int WriteUseCrawl(String link);
}

package com.github.hcsp;

import org.apache.http.HttpHost;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticSearch {
    public static void main(String[] args) {
        String resource = "db/mybatis/config.xml";
        SqlSessionFactory sqlSessionFactory = null;
        try {
            InputStream inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //取出前两千条数据'
        List<News> newsFormMysql = getNewsFormMysql(sqlSessionFactory);

        //开启多个线程写入数据
        for (int i = 0; i < 10; i++) {
            new Thread(() -> WriteOrigin(newsFormMysql)).start();
        }

    }

    /**
     * 获取mock数据
     * @param sqlSessionFactory 数据库连接
     * @return 返回前2000条mock数据
     */
    public static List<News> getNewsFormMysql(SqlSessionFactory sqlSessionFactory) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return session.selectList("com.github.hcsp.MockMapper.selectNews");
        }
    }

    public static void WriteOrigin(List<News> newsFormMysql) {
        try (RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http")))) {

            //MOCK数据写入搜索引擎 循环写入固定MOCK数据
            for (int i = 0; i < 100; i++) {
                //缓存固定数据量 再一次提交至搜索引擎
                BulkRequest bulkRequest = new BulkRequest();
                for (News news : newsFormMysql) {
                    IndexRequest request = new IndexRequest("news");
                    Map<String, Object> data = new HashMap<>();
                    data.put("content", news.getContent().length() > 10 ? news.getContent().substring(0,10) : news.getContent());
                    data.put("url", news.getUrl());
                    data.put("title", news.getTitle());
                    data.put("createdAt", news.getCreatedAt());
                    data.put("modifiedAt", news.getModifiedAt());
                    request.source(data, XContentType.JSON);
                    //写入索引

                    bulkRequest.add(request);
//                    IndexResponse response = client.index(request, RequestOptions.DEFAULT);
//
//                    System.out.println(response.status().getStatus());
                }
                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                System.out.println("写入进度：" + Thread.currentThread().getName() + "-线程-" + i + "-" + bulkResponse.status().getStatus());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

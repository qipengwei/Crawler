package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws IOException {
        //数据源链接池
        List<String> linkeds = new ArrayList<>();
        //使用过的链接池
        Set<String> useds = new HashSet<>();

        linkeds.add("https://news.sina.cn");

        //每次从数据源拿出一个链接
        while (true) {
            if (linkeds.isEmpty()) {
                break;
            }
            //拿到链接 创建请求
            String link = linkeds.remove(linkeds.size() - 1);
            //如果链接已被处理过
            if (useds.contains(link)) {
                //进行下次处理
                continue;
            }
            //处理链接中带有特定域名的链接
            if (Concerned(link)) {
                //链接转换为文档对象
                Document document = HttpGetParseHtml(link);
                //拿出所有<a/>标签 返回集合转换为stream流对象集合
                //格式化集合 筛选集合中a标签携带的链接 放入数据源链接池
                document.select("a")
                           .stream()
                           .map(tag -> tag.attr("href"))
                           .forEach(tag -> linkeds.add(tag));
                //筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
                WriteToDatabase(document);
                //处理完成之后 把处理过的链接放入特定的链接池中
                useds.add(link);
            }

        }

    }

    /**
     * 筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
     * @param document HTML转换后的文档对象
     */
    public static void WriteToDatabase(Document document) {
        //筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
        Elements articleTags = document.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                //爬取标题
                String resultTitle = articleTag.select(".art_tit_h1").get(0).text();
                System.out.println(resultTitle);
            }
        }
    }

    /**
     * 爬取链接转换为HTML节点
     * @param link
     * @return Document
     */
    public static Document HttpGetParseHtml(String link) {
        if (link.startsWith("//")) {
            link = "https:" + link;
            System.out.println(link + "加上https协议头链接");
        }

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(link);
        Document doc = null;
        //伪造浏览器请求 设置请求头
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1");
        try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
            //输出请求状态
            System.out.println(response.getStatusLine());
            //获取请求响应转换为HTML字符串
            HttpEntity entity = response.getEntity();
            String HTML = EntityUtils.toString(entity);
            //解析HTML
            doc = Jsoup.parse(HTML);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return doc;
    }
    /**
     * 检测链接是否有价值
     * @param link
     * @return boolean
     */
    public static boolean Concerned(String link) {
        return IsIndexPage(link) || IsNewsPage(link) && !IsLoginPage(link);
    }

    /**
     * 判断链接是否为主页
     * @param link
     * @return boolean
     */
    public static boolean IsIndexPage(String link) {
        return "https://sina.cn".equals(link);
    }

    /**
     * 判断链接是否为新闻页面
     * @param link
     * @return boolean
     */
    public static boolean IsNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    /**
     * 判断链接是否为登录页面
     * @param link
     * @return boolean
     */
    public static boolean IsLoginPage(String link) {
        return link.contains("passport.sina.cn");
    }
}

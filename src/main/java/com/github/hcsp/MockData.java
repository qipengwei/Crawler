package com.github.hcsp;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Random;

public class MockData {

    private static final int TAGET_ROW_COUNT = 100000;

    public static void main(String[] args) {
        String resource = "db/mybatis/config.xml";
        InputStream inputStream;
        SqlSessionFactory sqlSessionFactory = null;
        try {
            inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
            MockData(sqlSessionFactory, TAGET_ROW_COUNT);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void MockData(SqlSessionFactory sqlSessionFactory, int frequency) {
        try (SqlSession session =  sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            List<News> newsList = session.selectList("com.github.hcsp.MockMapper.selectNews");

            int count = frequency - newsList.size();
            Random random = new Random();
            try {
                while (count-- > 0) {
                    int index = random.nextInt(newsList.size());
                    //从newsList元素中随机取一个 插入 拷贝新news
                    News newsWrite = new News(newsList.get(index));
                    //Mock数据时间戳需要更改成唯一性
                    Instant currentTime = newsWrite.getCreatedAt();
                    //把时间戳更改为过去一年随机的一个时间
                    currentTime = currentTime.minusSeconds(random.nextInt(3600 * 24 * 365));

                    newsWrite.setCreatedAt(currentTime);
                    newsWrite.setModifiedAt(currentTime);

                    session.insert("com.github.hcsp.MockMapper.insertNews", newsWrite);

                    //缓存满两千条数据时一次性插入
                    if (count % 2000 == 0) {
                        session.flushStatements();
                    }

                    System.out.println("还剩" + count + "个");
                }
                //提交事务
                session.commit();
            } catch (Exception e) {
                //如果发生异常 进行事务回滚操作
                session.rollback();
                throw new RuntimeException(e);
            }

        }
    }
}

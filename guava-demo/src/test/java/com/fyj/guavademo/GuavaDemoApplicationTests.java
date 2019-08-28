package com.fyj.guavademo;

import com.fyj.guavademo.enetity.User;
import com.fyj.guavademo.service.UserService;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GuavaDemoApplicationTests {

    @Resource
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    private static final int THREAD_NUM = 1000;//并发线程的数量

    private static BloomFilter<String> bf;

    private static List<User> allUsers;

    @PostConstruct
    public void init(){
        //从数据获取数据，加载到布隆过滤器
        long start = System.currentTimeMillis();
        allUsers = userService.findAll();
        if (allUsers == null || allUsers.size() == 0) {
            return;
        }
        //创建布隆过滤器，默认误判率为0.03
        bf = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), allUsers.size());
        //误判率越低，数组长度延长，需要的哈希函数越多
        //example: bf = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), allUsers.size(),0.0001);
        //将数据存入布隆过滤器
        for (User user : allUsers) {
            bf.put(user.getName());
        }
        long end = System.currentTimeMillis();
        System.out.println("查询并且加载"+allUsers.size()+"条数据到布隆过滤器过滤完毕，总共耗时："+(end-start));
    }

    @Test
    public void cacheBreakDownTest(){
        long start = System.currentTimeMillis();
        allUsers = userService.findAll();
        //使用CyclicBarrier构建1000个线程并发
        CyclicBarrier cyclicBarrier = new CyclicBarrier(THREAD_NUM);
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_NUM);
        for (int i = 0; i < THREAD_NUM; i++) {
            executorService.execute(new BloomTestsConcurreny().new MyThread(cyclicBarrier,redisTemplate,userService));
        }
        executorService.shutdown();
        //判断是否所有的线程已经运行完成
        while (!executorService.isTerminated()) {

        }
        long end = System.currentTimeMillis();
        System.out.println("并发数："+THREAD_NUM+"，新建线程以及过滤总耗时"+(end-start)+"毫秒，演示");

    }

    public class MyThread implements Runnable {

        private CyclicBarrier cyclicBarrier;
        private RedisTemplate redisTemplate;
        private UserService userService;

        public MyThread(CyclicBarrier cyclicBarrier, RedisTemplate redisTemplate, UserService userService) {
            this.cyclicBarrier = cyclicBarrier;
            this.redisTemplate = redisTemplate;
            this.userService = userService;
        }

        @Override
        public void run() {
            //所有子线程等待，当子线程全部创建完成再一起并发执行后面的代码
            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }

            //1.1(测试：不拢过来重启判断不存在，拦截——如果没有布隆过滤器，将造成缓存穿透)
            //随机产生一个字符串，在布隆过滤器中不存在
            String randomUser = UUID.randomUUID().toString();
            //1.2(测试：布隆过滤器判断存在，从Redis缓存取值，如果Redis为空就查询数据库并且写入Redis)
            //从List中获取一个存在的用户
            //String randomuser = allUsers.get(new Random().nextInt(allUsers.size())).getName();
            String key = "key:" + randomUser;

            Date date1 = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            //如果布隆过滤器中不存在这个用户直接返回，将流量挡掉
//            if (!bf.mightContain(randomUser)) {
//                System.out.println(sdf.format(date1)+"布隆过滤器中不存在，非法请求");
//                return;
//            }
            //查询缓存，如果缓存中存在就直接返回缓存数据
            ValueOperations<String, String> operations = (ValueOperations) redisTemplate.opsForValue();

            Object cacheUser = operations.get(key);
            if (cacheUser != null) {
                Date date = new Date();
                System.out.println(sdf.format(date)+"命中redis缓存");
                return;
            }

            //TODO 防止并发重复写缓存，加锁
            synchronized (randomUser) {
                //如果缓存不存在就查询数据库
                List<User> all = userService.findAll();
                if (all == null || all.size() == 0) {
                    //很容易发生连接池不够用的情况 HikariPool-1 - Connection is not available
                    System.out.println("Redis缓存不存在，查询数据库也不存在，发生缓存穿透！！！");
                    return;
                }
                //将mysql数据库的查询结果写入redis
                Date data3 = new Date();
                System.out.println(sdf.format(data3)+"从数据库查询并且写入Redis");
                operations.set("key:" + all.get(0).getName(), all.get(0).getName());
            }
        }
    }

}

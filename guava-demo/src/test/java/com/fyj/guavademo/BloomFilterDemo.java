package com.fyj.guavademo;


import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BloomFilterDemo {

    private static final int insertions = 1000000;

    public static void main(String[] args) {

        //初始化存储string数据的布隆过滤器，初始化大小为100w
        //默认误判率为0.03
        BloomFilter<String> bf = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), insertions
        );

        //用于存放所有实际存在的key，判断key是否存在
        Set<String> sets = new HashSet<>(insertions);

        //用于存放所有实际存在的key,判断key是否存在
        List<String> lists = new ArrayList<>(insertions);

        //向三个容器初始化100w个随机并且唯一的字符串
        for (int i = 0; i < insertions; i++) {
            String uuid = UUID.randomUUID().toString();
            bf.put(uuid);
            sets.add(uuid);
            lists.add(uuid);
        }

        int right = 0;//正确判断的次数
        int wrong = 0;//错误判断的次数

        for (int i = 0; i < 10000; i++) {
            //可以被100整除的时候，取一个存在的数，否则随机生成一个UUID
            //0-10000之间，可以被100整除的有100个(100的倍数)
            String data = i % 100 == 0 ? lists.get(i / 100) : UUID.randomUUID().toString();

            if (bf.mightContain(data)){
                if (sets.contains(data)) {
                    //判断是否存在，命中
                    right++;
                    continue;
                }
                wrong++;
            }
        }

        NumberFormat percentFoemat = NumberFormat.getPercentInstance();
        percentFoemat.setMaximumFractionDigits(2);//最大小数位数
        float percent = (float)wrong / 9900;
        float bingo = (float) (9900 - wrong) / 9900;

        System.out.println("在100w个元素中，判断100个实际存在的元素，布隆过滤器认为存在的：" + right);
        System.out.println("在100w个元素中，判断9900个实际不存在的元素，误认为存在的："+wrong+""+"，命中率："
                +percentFoemat.format(bingo)+",误判率："
                +percentFoemat.format(percent));

    }

}

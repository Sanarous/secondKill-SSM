package com.study.seckill.service;


import com.study.seckill.dto.ExposerRsp;
import com.study.seckill.dto.SeckillExecutionRsp;
import com.study.seckill.entity.Seckill;
import com.study.seckill.exception.SeckillException;

import java.util.List;

/**
 * 业务接口：站在“使用者”的角度设计接口
 * 三个方面：方法定义粒度，参数，返回类型（return 类型/异常）
 */
public interface SeckillService {

    /**
     * 查询所有秒杀记录
     *
     * @return
     */
    List<Seckill> getSeckillList();

    /**
     * 查询单个秒杀记录
     *
     * @param seckillId
     * @return
     */
    Seckill getById(long seckillId);

    /**
     * 秒杀开启时，输出秒杀接口地址，
     * 否则输出系统时间和秒杀时间
     *
     * @param seckillId
     */
    ExposerRsp exprotSeckillUrl(long seckillId);

    /**
     * 执行秒杀操作
     *
     * @param seckillId
     * @param userPhone
     * @param md5
     */
    SeckillExecutionRsp executeSeckill(long seckillId, long userPhone, String md5) throws SeckillException;

    /**
     * 执行秒杀操作 by 存储过程
     *
     * @param seckillId
     * @param userPhone
     * @param md5
     */
    SeckillExecutionRsp executeSeckillProducer(long seckillId, long userPhone, String md5) throws SeckillException;
}

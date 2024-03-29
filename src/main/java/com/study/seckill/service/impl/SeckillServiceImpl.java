package com.study.seckill.service.impl;

import com.study.seckill.dao.SeckillDao;
import com.study.seckill.dao.SuccessKilledDao;
import com.study.seckill.dao.cache.RedisDao;
import com.study.seckill.dto.ExposerRsp;
import com.study.seckill.dto.SeckillExecutionRsp;
import com.study.seckill.entity.Seckill;
import com.study.seckill.entity.SuccessKilled;
import com.study.seckill.enums.SeckillStatEnum;
import com.study.seckill.exception.RepeatKillException;
import com.study.seckill.exception.SeckillCloseException;
import com.study.seckill.exception.SeckillException;
import com.study.seckill.service.SeckillService;
import com.sun.xml.internal.ws.policy.PolicyMapUtil;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeckillServiceImpl implements SeckillService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SeckillDao seckillDao;

    @Autowired
    private SuccessKilledDao successKilledDao;

    @Autowired
    private RedisDao redisDao;

    //md5盐值字符串，用于混淆MD5
    private final String slat = "sdaafdalk21dsaasmdl^&%^";

    /**
     * 获取秒杀商品信息列表
     * @return
     */
    @Override
    public List<Seckill> getSeckillList() {
        return seckillDao.queryAll(0, 6);
    }

    /**
     * 通过id查询秒杀商品信息
     * @param seckillId
     * @return
     */
    @Override
    public Seckill getById(long seckillId) {
        return seckillDao.queryById(seckillId);
    }

    /**
     * 暴露秒杀接口
     * @param seckillId
     * @return
     */
    @Override
    public ExposerRsp exprotSeckillUrl(long seckillId) {
        //缓存优化，使用redis
        Seckill seckill = redisDao.getSeckill(seckillId);
        if(seckill == null){
            //访问数据库
            seckill = seckillDao.queryById(seckillId);
            if(seckill == null){
                return new ExposerRsp(false, seckillId);
            }else{
                //放入redis
                redisDao.putSeckill(seckill);
            }
        }
        //秒杀时间
        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();
        //系统时间
        Date nowTime = new Date();
        if (nowTime.getTime() < startTime.getTime() || nowTime.getTime() > endTime.getTime()) {
            return new ExposerRsp(false, seckillId, nowTime.getTime(), startTime.getTime(), endTime.getTime());
        }
        //转化特定字符串的过程，不可逆
        String md5 = getMD5(seckillId);
        return new ExposerRsp(true, md5, seckillId);
    }

    /**
     * 生成MD5
     * @param seckillId
     * @return
     */
    private String getMD5(long seckillId) {
        String base = seckillId + "/" + slat;
        String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
        return md5;
    }


    /**
     * 开启秒杀
     * 使用注解控制事务方法的优点
     * 1.开发团队达成一致约定，明确标注事务方法的编程风格
     * 2.保证事务方法的执行时间尽可能短，不要穿插其他网络操作，RPC/HTTP请求 或者 剥离到事务方法之外
     * 3.不是所有的方法都需要事务，如只有一条修改操作，只读操作不需要事务控制。
     */
    @Override
    @Transactional
    public SeckillExecutionRsp executeSeckill(long seckillId, long userPhone, String md5) throws SeckillException, RepeatKillException, SeckillCloseException {
        if (md5 == null || !md5.equals(getMD5(seckillId))) {
            throw new SeckillException("seckill data rewrite");
        }
        //执行秒杀逻辑：减库存 + 记录购买行为
        Date nowTime = new Date();
        try {
            // 记录购买行为，使用Insert ignore执行
            int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
            if (insertCount <= 0) {
                //重复秒杀
                throw new RepeatKillException("seckill repeated");
            } else {
                //减库存，热点商品竞争
                int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
                if (updateCount <= 0) {
                    //没有更新到记录，秒杀结束，rollback
                    throw new SeckillCloseException("seckill is closed");
                } else {
                    //秒杀成功，commit
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecutionRsp(seckillId, SeckillStatEnum.SUCCESS, successKilled);
                }
            }
        } catch (SeckillCloseException e1) {
            throw e1;
        } catch (RepeatKillException e2) {
            throw e2;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            //所有编译期异常转化为运行期异常
            throw new SeckillException("seckill inner error:" + e.getMessage());
        }
    }

    /**
     * 使用存储过程完成秒杀逻辑
     * @param seckillId
     * @param userPhone
     * @param md5
     * @return
     * @throws SeckillException
     */
    @Override
    public SeckillExecutionRsp executeSeckillProducer(long seckillId, long userPhone, String md5) throws SeckillException {
        if(md5 == null || md5.equals(getMD5(seckillId))){
            return new SeckillExecutionRsp(seckillId,SeckillStatEnum.DATA_REWRITE);
        }
        Date killTime = new Date();
        Map<String,Object> map = new HashMap<>();
        map.put("seckillId",seckillId);
        map.put("phone",userPhone);
        map.put("killTime",killTime);
        map.put("result",null);
        //执行存储过程完成之后，result被复制
        try{
            seckillDao.killByProcedure(map);
            //获取result
            int result = MapUtils.getInteger(map, "result", -2);
            if(result == 1){
                SuccessKilled sk = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                return new SeckillExecutionRsp(seckillId,SeckillStatEnum.SUCCESS,sk);
            }else{
                return new SeckillExecutionRsp(seckillId,SeckillStatEnum.stateOf(result));
            }
        }catch (Exception e){
            logger.error(e.getMessage(),e);
            //抛出系统内部异常
            return new SeckillExecutionRsp(seckillId,SeckillStatEnum.INNER_ERROR);
        }
    }
}

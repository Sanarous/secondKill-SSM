-- 秒杀执行存储过程
DELIMITER $$  -- console  ; 转换成 $$ 转换成换行符
-- 参数： in：输入参数  out：输出参数
-- count_count():返回上一条修改类型sql(delete,insert,update)的影响行数
-- row_count:0 未修改数据 >0 表示修改的行数 <0 : sql错误，未执行修改sql
CREATE PROCEDURE `seckill`.`execute_seckill`
  (in v_seckill_id bigint,int v_phone bigint,
   in v_kill_time timestamp ,out r_result int)
   begin
   declare  insert _count int default 0;
   start transaction ;
   insert ignore into success_killed
      (seckill_id,user_phone,create_time)
      values (v_seckill_id,v_phone,v_kill_time)
    select row_count() into insert_count;
    IF(insert_count < 0) then
      rollback ;
      set r_result = -1;
    else if(insert_count < 0) then
      rollback ;
      set r_result = -2;
    else
      update seckill
      set number = number -1
      where seckill_id = v_seckill_id
            and end_time > v_kill_time
            and start_time < v_kill_time
            and number >0;
      select row_count() into insert_count;
      IF(insert_count = 0) then
         rollback ;
         set r_result = 0;
      ELSEIF (insert_count < 0) then
        rollback ;
        set r_result = -2;
      else
         commit;
         set r_result = 1;
      end if;
    end if;
  end;
$$
-- 存储过程定义结束

DELIMITER ;
set @r_result = -3;
call execute_seckill(1003,18971204096,now(),@r_result);

-- 获取结果
select @r_result;

-- 存储过程
-- 1.优化的是事务行级锁持有的时间
-- 2.不要过度依赖存储过程
-- 3.简单的逻辑可以应用存储过程
-- 4.一个秒杀单接近6000/QPS
package com.study.seckill.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Seckill {

    private long seckillId;

    private String name;

    private int number;

    private Date startTime;

    private Date endTime;

    private Date createTime;
}

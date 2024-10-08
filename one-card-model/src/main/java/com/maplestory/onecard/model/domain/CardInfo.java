package com.maplestory.onecard.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 卡牌信息
 * @TableName card_info
 */
@TableName(value ="card_info")
@Data
public class CardInfo implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 颜色
     */
    private String color;

    /**
     * 点数
     */
    private String point;

    /**
     * 描述
     */
    @JsonIgnore
    private String description;

    /**
     * 图片路径
     */
    @JsonIgnore
    private String image;

    /**
     * 1-可进牌堆，0-不可进
     */
    @JsonIgnore
    private Integer available;

    /**
     * 0-数字牌，1-功能牌，2-变色伊莉娜，3-卡背
     */
    @JsonIgnore
    private Integer cardType;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;


}
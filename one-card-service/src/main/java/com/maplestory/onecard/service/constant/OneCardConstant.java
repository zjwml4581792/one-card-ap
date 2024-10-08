package com.maplestory.onecard.service.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OneCardConstant {
    public static final String Code_Success = "000";
    public static final String Code_OtherFail = "999";
    public static final String Msg_Success = "成功";

    public static final String User_Status_Disabled = "0";
    public static final String User_Status_Available = "1";
    /**
     * 未开始
     */
    public static final String Battle_Status_waiting = "00";
    /**
     * 进行中
     */
    public static final String Battle_Status_battling = "01";
    /**
     * 已结束
     */
    public static final String Battle_Status_end = "02";
    /**
     * 选颜色
     */
    public static final String Battle_Status_changing = "03";

    public static final Integer Card_Type_Digit = 0;

    public static final Integer Card_Type_Ability = 1;

    /**
     * 攻击上限
     */
    public static final Integer Attack_Max = 12;

    /**
     * 手牌上限
     */
    public static final Integer Hand_Max = 20;

    /**
     * 总共就49张牌
     */
    public static final Integer Deck_Max = 49;

    /**
     * 卡背的图片
     */
    public static final Long Card_Back = 53L;

    public static final Long Card_Mihile = 48L;

    public static final Long Card_Icart = 49L;

    public static final Long Card_Oz = 36L;
}

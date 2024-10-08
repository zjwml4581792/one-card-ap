-- onecard.user_info definition

CREATE TABLE `user_info`
(
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
    `user_name` varchar(100) NOT NULL COMMENT '昵称',
    `user_id` varchar(100) DEFAULT NULL COMMENT '用户名',
    `status` varchar(2) DEFAULT '0' COMMENT '0-正常，1-其他',
    `room_number` varchar(10) DEFAULT NULL COMMENT '所在房间',
    `hand` varchar(100) DEFAULT NULL COMMENT '辅助字段，手牌',
    `ppp` varchar(100) DEFAULT '123456',
    PRIMARY KEY (`id`)
) COMMENT ='用户信息表';
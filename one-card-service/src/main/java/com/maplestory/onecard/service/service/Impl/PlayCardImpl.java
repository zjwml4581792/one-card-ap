package com.maplestory.onecard.service.service.Impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplestory.onecard.model.domain.BattleInfo;
import com.maplestory.onecard.model.domain.CardInfo;
import com.maplestory.onecard.model.domain.UserInfo;
import com.maplestory.onecard.service.constant.OneCardConstant;
import com.maplestory.onecard.service.service.PlayCard;
import com.maplestory.onecard.service.util.ListUtils;
import com.maplestory.onecard.service.vo.BattleInfoSubOutVo;
import com.maplestory.onecard.service.vo.PlayCardInVo;
import com.maplestory.onecard.service.vo.PlayCardOutVo;
import com.maplestory.onecard.service.vo.ResponseJson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class PlayCardImpl extends CommonService implements PlayCard {
    private final String log001 = "PlayCardImpl happened:";

    @SneakyThrows
    @Override
    public ResponseJson<PlayCardOutVo> doService(PlayCardInVo inVo) {
        log.info("{}----------交易开始--------", log001);

        UserInfo userInfo = userInfoMapper.selectByUserName(inVo.getUserName());
        if (null == userInfo) {
            log.error("{}--------用户不存在:{}-----", log001, inVo.getUserName());
            return ResponseJson.failure(OneCardConstant.Code_OtherFail, "用户不存在");
        }

        List<BattleInfo> battleInfoList = battleInfoMapper.selectByRoomNumber(inVo.getRoomNumber());
        if (battleInfoList.isEmpty()) {
            log.error("{}--------房间不存在:{}-----", log001, inVo.getRoomNumber());
            return ResponseJson.failure(OneCardConstant.Code_OtherFail, "房间不存在");
        }
        if (battleInfoList.size() > 1) {
            log.error("{}--------房间不唯一:{}-----", log001, inVo.getRoomNumber());
            return ResponseJson.failure(OneCardConstant.Code_OtherFail, "房间异常不唯一，请联系管理员");
        }
        BattleInfo battleInfo = battleInfoList.get(0);

        CardInfo cardInfo = cardInfoMapper.selectByPrimaryKey(Long.valueOf(inVo.getCardId()));
        //检查牌是否能出
        if (this.isUnavailableCard(battleInfo, cardInfo, userInfo)) {
            log.error("{}--------不能出这张牌:{}-----", log001, inVo.getRoomNumber());
            return ResponseJson.failure(OneCardConstant.Code_OtherFail, "不能出这张牌");
        }
        //开始处理出牌
        //先处理非实体牌
        List<UserInfo> players = objectMapper.readValue(battleInfo.getPlayers(), objectMapper.getTypeFactory().constructParametricType(List.class, UserInfo.class));
        //选好了变色伊莉娜或者是选好了变色牌
        if (OneCardConstant.Battle_Status_changing.equals(battleInfo.getStatus())) {
            battleInfo.setStatus(OneCardConstant.Battle_Status_battling);

            addPlayCardIntoDeck(battleInfo);

            battleInfo.setPlayCard(objectMapper.writeValueAsString(cardInfo));
            battleInfo.setTurn((battleInfo.getTurn() + players.size() + battleInfo.getDirection()) % players.size());
            battleInfo.setPlayPlayer(userInfo.getId());
            battleInfoMapper.updateByPrimaryKey(battleInfo);
            PlayCardOutVo outVo = new PlayCardOutVo();
            BattleInfoSubOutVo battleInfoSubOutVo = getBattleInfoSubOutVo(battleInfo, userInfo);
            outVo.setBattleInfoSubOutVo(battleInfoSubOutVo);
            return ResponseJson.ok(outVo);
        }
        log.info("{}---------房间[{}]，开始处理玩家[{}]出牌", log001, inVo.getRoomNumber(), inVo.getUserName());
        List<CardInfo> hand = getHand(players, userInfo);
        playOneCard(hand, cardInfo);
        setHand(players, userInfo, hand);
        battleInfo.setPlayers(objectMapper.writeValueAsString(players));

        log.info("{}---------房间[{}]开始判断玩家[{}]是否赢了", log001, inVo.getRoomNumber(), inVo.getUserName());
        //手牌为空就是赢了
        if (hand.isEmpty()) {
            log.info("{}---------房间[{}]，玩家[{}]赢了，游戏结束", log001, inVo.getRoomNumber(), inVo.getUserName());
            //设置出牌者和结束就行了
            battleInfo.setPlayPlayer(userInfo.getId());
            battleInfo.setStatus(OneCardConstant.Battle_Status_waiting);
            battleInfoMapper.updateByPrimaryKey(battleInfo);
//            所有人的房间号置空
            for (UserInfo player : players) {
                player.setRoomNumber("");
                userInfoMapper.clearRoomNumberByPrimaryKey(player.getId());
            }
            PlayCardOutVo outVo = new PlayCardOutVo();
            BattleInfoSubOutVo battleInfoSubOutVo = getBattleInfoSubOutVo(battleInfo, userInfo);
            outVo.setBattleInfoSubOutVo(battleInfoSubOutVo);
            return ResponseJson.ok(outVo);
        }
        //接下来是战斗继续
        log.info("{}---------房间[{}]，玩家[{}]还有手牌，战斗继续", log001, inVo.getRoomNumber(), inVo.getUserName());
        //刚出变色牌
        if ("change".equals(cardInfo.getPoint())) {
            battleInfo.setStatus(OneCardConstant.Battle_Status_changing);

            addPlayCardIntoDeck(battleInfo);
            battleInfo.setPlayCard(objectMapper.writeValueAsString(cardInfo));

            battleInfoMapper.updateByPrimaryKey(battleInfo);
            PlayCardOutVo outVo = new PlayCardOutVo();
            BattleInfoSubOutVo battleInfoSubOutVo = getBattleInfoSubOutVo(battleInfo, userInfo);
            outVo.setBattleInfoSubOutVo(battleInfoSubOutVo);
            return ResponseJson.ok(outVo);
        }
        //反转
        if ("goback".equals(cardInfo.getPoint())) {
            battleInfo.setDirection(-battleInfo.getDirection());

            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //跳过
        if ("jump".equals(cardInfo.getPoint())) {
            battleInfo.setTurn((battleInfo.getTurn() + players.size() + battleInfo.getDirection()) % players.size());

            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //攻击2
        if ("attack2".equals(cardInfo.getPoint())) {
            battleInfo.setAttackLevel(Integer.min(OneCardConstant.Attack_Max, battleInfo.getAttackLevel() + 2));
            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //攻击3
        if ("attack3".equals(cardInfo.getPoint())) {
            battleInfo.setAttackLevel(Integer.min(OneCardConstant.Attack_Max, battleInfo.getAttackLevel() + 3));
            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //奥兹
        if ("hero".equals(cardInfo.getPoint()) && "red".equals(cardInfo.getColor())) {
            battleInfo.setAttackLevel(Integer.min(OneCardConstant.Attack_Max, battleInfo.getAttackLevel() + 5));
            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //米哈尔
        if ("hero".equals(cardInfo.getPoint()) && "yellow".equals(cardInfo.getColor())) {
            battleInfo.setAttackLevel(0);
            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //胡克
        if ("hero".equals(cardInfo.getPoint()) && "blue".equals(cardInfo.getColor())) {
            addTwoToOthers(battleInfo, userInfo);
            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //伊莉娜
        if ("hero".equals(cardInfo.getPoint()) && "green".equals(cardInfo.getColor())) {
            //检查每个人的手牌，删除绿色的，都放进牌堆
            deleteGreenCard(battleInfo);
            battleInfo.setStatus(OneCardConstant.Battle_Status_changing);
            return nextTurn(userInfo, battleInfo, cardInfo);
        }
        //如果是数字牌
        if (cardInfo.getPoint().matches("^\\d$")) {
            nextTurn(userInfo, battleInfo, cardInfo);
        }
        //伊卡尔特
        if ("hero".equals(cardInfo.getPoint()) && "black".equals(cardInfo.getColor())) {
            battleInfo.setTurn((battleInfo.getTurn() + players.size() + battleInfo.getDirection()) % players.size());

            battleInfoMapper.updateByPrimaryKey(battleInfo);
            PlayCardOutVo outVo = new PlayCardOutVo();
            BattleInfoSubOutVo battleInfoSubOutVo = getBattleInfoSubOutVo(battleInfo, userInfo);
            outVo.setBattleInfoSubOutVo(battleInfoSubOutVo);
            return ResponseJson.ok(outVo);
        }


        PlayCardOutVo outVo = new PlayCardOutVo();
        BattleInfoSubOutVo battleInfoSubOutVo = getBattleInfoSubOutVo(battleInfo, userInfo);
        outVo.setBattleInfoSubOutVo(battleInfoSubOutVo);
        return ResponseJson.ok(outVo);
    }

    private void playOneCard(List<CardInfo> hand, CardInfo cardInfo) {
        for (int i = 0; i < hand.size(); i++) {
            CardInfo s = hand.get(i);
            if (Objects.equals(s.getId(), cardInfo.getId())) {
                hand.remove(i);
                break;
            }
        }
    }

    @SneakyThrows
    private ResponseJson<PlayCardOutVo> nextTurn(UserInfo userInfo, BattleInfo battleInfo, CardInfo cardInfo) {
        List<UserInfo> players = objectMapper.readValue(battleInfo.getPlayers(), objectMapper.getTypeFactory().constructParametricType(List.class, UserInfo.class));
        if (!battleInfo.getStatus().equals(OneCardConstant.Battle_Status_changing)) {
            battleInfo.setTurn((battleInfo.getTurn() + players.size() + battleInfo.getDirection()) % players.size());
        }
        CardInfo previousCard = objectMapper.readValue(battleInfo.getPlayCard(), CardInfo.class);
        if (!"change".equals(previousCard.getPoint())) {
            addPlayCardIntoDeck(battleInfo);
        }
        battleInfo.setPlayCard(objectMapper.writeValueAsString(cardInfo));
        battleInfo.setPlayPlayer(userInfo.getId());

        battleInfoMapper.updateByPrimaryKey(battleInfo);
        PlayCardOutVo outVo = new PlayCardOutVo();
        BattleInfoSubOutVo battleInfoSubOutVo = getBattleInfoSubOutVo(battleInfo, userInfo);
        outVo.setBattleInfoSubOutVo(battleInfoSubOutVo);
        return ResponseJson.ok(outVo);
    }

    /**
     * 胡克给其他每个人都加2张卡
     *
     * @param userInfo   出胡克的人
     * @param battleInfo 战斗信息
     */
    @SneakyThrows
    private void addTwoToOthers(BattleInfo battleInfo, UserInfo userInfo) {
        List<CardInfo> deck = objectMapper.readValue(battleInfo.getDeck(), objectMapper.getTypeFactory().constructParametricType(List.class, CardInfo.class));
        List<UserInfo> players = objectMapper.readValue(battleInfo.getPlayers(), objectMapper.getTypeFactory().constructParametricType(List.class, UserInfo.class));

        for (UserInfo player : players) {
            if (!player.getId().equals(userInfo.getId())) {
                List<CardInfo> hand = getHand(players,userInfo);
                hand.addAll(getCards(deck, 2));
                setHand(players,userInfo,hand);
            }
        }
        battleInfo.setDeck(objectMapper.writeValueAsString(deck));
        battleInfo.setPlayers(objectMapper.writeValueAsString(players));
    }

    /**
     * 伊莉娜从手牌删除绿卡，放回牌堆
     *
     * @param battleInfo 战斗信息
     */
    @SneakyThrows
    private void deleteGreenCard(BattleInfo battleInfo) {
        List<CardInfo> deck = objectMapper.readValue(battleInfo.getDeck(), objectMapper.getTypeFactory().constructParametricType(List.class, CardInfo.class));
        List<UserInfo> players = objectMapper.readValue(battleInfo.getPlayers(), objectMapper.getTypeFactory().constructParametricType(List.class, UserInfo.class));

        for (UserInfo player : players) {
            List<CardInfo> hand = objectMapper.readValue(player.getHand(), objectMapper.getTypeFactory().constructParametricType(List.class, CardInfo.class));
            for (int i = hand.size() - 1; i >= 0; i--) {
                CardInfo greenCard = hand.get(i);
                //12-24是绿卡
                if (12 < greenCard.getId() && greenCard.getId() < 24) {
                    deck.add(greenCard);
                    hand.remove(i);
                }
            }
            player.setHand(objectMapper.writeValueAsString(hand));
        }
        battleInfo.setDeck(objectMapper.writeValueAsString(deck));
        battleInfo.setPlayers(objectMapper.writeValueAsString(players));
    }

    /**
     * 把上一张卡加入牌堆
     *
     * @param battleInfo 战斗信息
     * @throws RuntimeException jackson错误
     */
    @SneakyThrows
    private void addPlayCardIntoDeck(BattleInfo battleInfo) {
        List<CardInfo> deck = objectMapper.readValue(battleInfo.getDeck(), objectMapper.getTypeFactory().constructParametricType(List.class, CardInfo.class));
        CardInfo playCard = objectMapper.readValue(battleInfo.getPlayCard(), CardInfo.class);
        deck.add(playCard);
        battleInfo.setDeck(objectMapper.writeValueAsString(deck));
    }

    /**
     * 检查这个牌能不能出
     *
     * @param battleInfo 战斗信息
     * @param cardInfo   卡信息
     * @param userInfo   用户
     * @return Boolean
     */
    @SneakyThrows
    private boolean isUnavailableCard(BattleInfo battleInfo, CardInfo cardInfo, UserInfo userInfo) {
        List<UserInfo> players = objectMapper.readValue(battleInfo.getPlayers(), objectMapper.getTypeFactory().constructParametricType(List.class, UserInfo.class));
        int pos = getPos(players, userInfo);
        //不在牌桌上，有人攻击！不能出
        if (pos == -1) {
            log.info("{}------房间[{}]没有[{}],", log001, battleInfo.getRoomNumber(), userInfo.getUserName());
            return true;
        }
        if (!players.get(battleInfo.getTurn().intValue()).getId().equals(userInfo.getId())) {
            log.info("{}------房间[{}]此时不该[{}]出牌,", log001, battleInfo.getRoomNumber(), userInfo.getUserName());
            return true;
        }
        //变色的时候可以出的牌
        if (battleInfo.getStatus().equals(OneCardConstant.Battle_Status_changing)) {
            if (cardInfo.getPoint().equals("change")) {
                return false;
            } else if (cardInfo.getPoint().equals("hero")) {
                return false;
            } else {
                return true;
            }
        }

        if ("black".equals(cardInfo.getColor())) {
            return false;
        }
        CardInfo previousCard = objectMapper.readValue(battleInfo.getPlayCard(), CardInfo.class);
        //出了攻击2可以出攻击3和奥兹
        if ("attack2".equals(previousCard.getPoint())) {
            if (0 != battleInfo.getAttackLevel()) {
                if ("attack2".equals(cardInfo.getPoint())) {
                    return false;
                } else if ("attack3".equals(cardInfo.getPoint())) {
                    return false;
                } else if ("hero".equals(cardInfo.getPoint()) && "red".equals(cardInfo.getColor())) {
                    return false;
                } else {
                    return !cardInfo.getId().equals(OneCardConstant.Card_Mihile) && !cardInfo.getId().equals(OneCardConstant.Card_Icart);
                }
            }
        }
        //出了攻击3可以出奥兹
        if ("attack3".equals(previousCard.getPoint())) {
            if (0 != battleInfo.getAttackLevel()) {
                if ("attack3".equals(cardInfo.getPoint())) {
                    return false;
                } else if ("hero".equals(cardInfo.getPoint()) && "red".equals(cardInfo.getColor())) {
                    return false;
                } else {
                    return !cardInfo.getId().equals(OneCardConstant.Card_Mihile) && !cardInfo.getId().equals(OneCardConstant.Card_Icart);
                }
            }
        }
        if (OneCardConstant.Card_Oz.equals(previousCard.getId())) {
            if (0 != battleInfo.getAttackLevel()) {
                return !cardInfo.getId().equals(OneCardConstant.Card_Mihile) && !cardInfo.getId().equals(OneCardConstant.Card_Icart);
            }
        }
        //出了英雄牌就只能出同颜色的
        if ("hero".equals(previousCard.getPoint()) && !Objects.equals(previousCard.getColor(), cardInfo.getColor())) {
            return true;
        }

        return !Objects.equals(previousCard.getColor(), cardInfo.getColor()) && !Objects.equals(previousCard.getPoint(), cardInfo.getPoint());
    }
}

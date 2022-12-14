package com.ssafy.moti.service.item;

import com.ssafy.moti.common.enums.EventType;
import com.ssafy.moti.common.enums.GoodsType;
import com.ssafy.moti.common.enums.LevelType;
import com.ssafy.moti.entity.dev.item.*;
import com.ssafy.moti.entity.dev.moti.MotiCatalog;
import com.ssafy.moti.entity.dev.moti.UserMotiInfo;
import com.ssafy.moti.entity.dev.moti.UserMotiStatus;
import com.ssafy.moti.entity.dev.user.User;
import com.ssafy.moti.entity.log.UserGoodsLog;
import com.ssafy.moti.entity.log.UserItemLog;
import com.ssafy.moti.repository.dev.item.ItemCatalogRepository;
import com.ssafy.moti.repository.dev.item.ShopRepository;
import com.ssafy.moti.repository.dev.item.UserInventoryRepository;
import com.ssafy.moti.repository.dev.moti.MotiCatalogRepository;
import com.ssafy.moti.repository.dev.moti.UserMotiInfoRepository;
import com.ssafy.moti.repository.dev.moti.UserMotiStatusRepository;
import com.ssafy.moti.repository.dev.user.UserGoodsRepository;
import com.ssafy.moti.repository.dev.user.UserRepository;
import com.ssafy.moti.repository.log.UserGoodsLogRepository;
import com.ssafy.moti.repository.log.UserItemLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ItemCatalogRepository itemCatalogRepository;

    @Autowired
    ShopRepository shopRepository;

    @Autowired
    UserInventoryRepository userInventoryRepository;

    @Autowired
    UserGoodsRepository userGoodsRepository;

    @Autowired
    UserGoodsLogRepository userGoodsLogRepository;

    @Autowired
    UserItemLogRepository userItemLogRepository;

    @Autowired
    UserMotiInfoRepository userMotiInfoRepository;

    @Autowired
    UserMotiStatusRepository userMotiStatusRepository;

    @Autowired
    MotiCatalogRepository motiCatalogRepository;

    @Override
    public List<ItemCatalog> viewShop() {
        List<Shop> shopList = shopRepository.findAll();
        List<ItemCatalog> result = new ArrayList<>();
        for(Shop shop: shopList) {
            result.add(shop.getItemCatalog());
        }
        return result;
    }

    @Override
    public Map<GoodsType, Long> viewUserGoods(Long user_userNo) {
        List<UserGoods> userGoods = userGoodsRepository.findUserGoodsByUser_UserNo(user_userNo);

        Map<GoodsType, Long> result = new HashMap<>();
        for(UserGoods userGood: userGoods) {
            result.put(userGood.getGoodsType(), userGood.getGoodsCount());
        }
        return result;
    }

    @Override
    public List<UserInventory> viewInventory(Long userNo) {
        return userInventoryRepository.findUserInventoriesByUser_UserNoAndItemCountIsGreaterThan(userNo, 0L);
    }

    @Override
    public ItemCatalog findByItemCatalogNo(Long itemCatalogNo) {
        return itemCatalogRepository.findByItemCatalogNo(itemCatalogNo);
    }

    @Override
    public boolean canUserBuyItem(Long userNo, Long itemCatalogNo) {
        // ?????? ???????????? ?????? ????????? ?????? ???????????? ???????????? ?????? ?????? ?????? ????????? ???????????? ??????
        ItemCatalog itemUserWantsToPurchase = findByItemCatalogNo(itemCatalogNo);

        Long goodsUserGot = userGoodsRepository.findByUser_UserNoAndGoodsType(
                userNo, itemUserWantsToPurchase.getItemGoodsType()
        ).getGoodsCount();

        return goodsUserGot >= itemUserWantsToPurchase.getItemPrice();
    }

    @Override
    public void buyItem(Long userNo, Long itemCatalogNo) {

        LocalDate timerDate = LocalDate.now();
        LocalTime timerTime = LocalTime.now();

        ItemCatalog itemCatalog = itemCatalogRepository.findByItemCatalogNo(itemCatalogNo);

        UserGoods userGoods = userGoodsRepository.findByUser_UserNoAndGoodsType(userNo, itemCatalog.getItemGoodsType());

        User user = userRepository.findByUserNo(userNo).orElse(null);

        // ????????? ????????? inventory??? ????????? ??????
        UserInventory userInventory;
        if(userInventoryRepository.existsByUserAndItemCatalog(user, itemCatalog)) {
            userInventory = userInventoryRepository.findByUserAndItemCatalog(
                    user, itemCatalog
            );
        } else {
            userInventory = userInventoryRepository.saveAndFlush(UserInventory.builder()
                    .user(user)
                    .itemCatalog(itemCatalog)
                    .itemCount(0L)
                    .build()
            );
        }

        log.debug("userInventory: "+userInventory.toString());

        UserInventoryID userInventoryID = new UserInventoryID(userNo, itemCatalogNo);

        log.debug("userInventoryID: "+userInventoryID);

        // ???????????? ?????? ??????
        userItemLogRepository.save(
                UserItemLog.builder()
                        .timerDate(timerDate.toString())
                        .timerTime(timerTime.toString())
                        .userNo(userNo)
                        .itemCatalogNo(itemCatalogNo)
                        .countBeforeEvent(userInventory.getItemCount())
                        .countAfterEvent(userInventory.getItemCount() + 1)
                        .eventType(EventType.PURCHASE)
                        .build()
        );

        // ?????? ?????? ??????
        userGoodsLogRepository.save(
                UserGoodsLog.builder()
                        .timerDate(timerDate.toString())
                        .timerTime(timerTime.toString())
                        .userNo(userNo)
                        .countBeforeEvent(userGoods.getGoodsCount())
                        .countAfterEvent(userGoods.getGoodsCount() - itemCatalog.getItemPrice())
                        .eventType(EventType.PURCHASE)
                        .goodsType(itemCatalog.getItemGoodsType())
                        .build()
        );
    }

    /**
     * ???????????? ??? ??????
     * @param userNo ????????? no
     */
    // TODO: ????????? ?????? ??????
    @Override
    @Transactional
    public void useReset(Long userNo) throws IllegalAccessException, IllegalStateException {
        LocalDate timerDate = LocalDate.now();
        LocalTime timerTime = LocalTime.now();

        Optional<UserInventory> userInventory =
                userInventoryRepository.findUserInventoryByUser_UserNoAndItemCatalog_ItemCatalogNo(userNo, 1L);

        Optional<User> user = userRepository.findByUserNo(userNo);

        // ???????????? ????????? inventory ????????? ?????? ??????
        if(user.isEmpty() || userInventory.isEmpty()) {
            throw new IllegalAccessException();
        }

        // ????????? ?????? ?????? ??????
        UserMotiStatus userMotiStatus = userMotiStatusRepository.findByUser(user.get());

        // ????????? ?????? ?????? ??????
        UserMotiInfo userMotiInfo = userMotiInfoRepository.findByUser(user.get());

        if(userMotiInfo == null) { // ????????? ?????? ??????
            throw new IllegalAccessException();
        }

        // ????????? 0????????? ?????? ????????? ??? ??????
        if(userMotiInfo.getMotiCatalog().getMotiLevel() == LevelType.ZERO) {
            throw new IllegalStateException();
        }

        // ?????? ?????? ????????? 0????????? ?????? catalog no ??????
        MotiCatalog returnEgg = motiCatalogRepository.findByMotiTypeAndMotiLevel(
                userMotiInfo.getMotiCatalog().getMotiType(), LevelType.ZERO
        );

        // ?????????
        userMotiInfo.setMotiCatalog(returnEgg);
        userMotiStatus.setSurvivalDays(0L);


        // ????????? ?????? ?????? ??????
        userMotiInfoRepository.save(userMotiInfo);
        userMotiStatusRepository.save(userMotiStatus);

        // ?????? ?????? -> ????????? ?????? ??????
        userItemLogRepository.save(UserItemLog.builder()
                .userNo(userNo)
                .timerDate(timerDate.toString())
                .timerTime(timerTime.toString())
                .itemCatalogNo(userInventory.get().getItemCatalog().getItemCatalogNo())
                .countBeforeEvent(userInventory.get().getItemCount())
                .countAfterEvent(userInventory.get().getItemCount() - 1)
                .eventType(EventType.USE)
                .build()
        );
    }

    @Override
    @Transactional
    public void useNameTag(Long userNo, String message) throws IllegalAccessException, IllegalArgumentException {
        LocalDate timerDate = LocalDate.now();
        LocalTime timerTime = LocalTime.now();

        Optional<UserInventory> userInventory =
                userInventoryRepository.findUserInventoryByUser_UserNoAndItemCatalog_ItemCatalogNo(userNo, 2L);

        Optional<User> user = userRepository.findByUserNo(userNo);

        if(user.isEmpty() || userInventory.isEmpty()) {
            throw new IllegalAccessException();
        }

        if(!userMotiInfoRepository.existsUserMotiInfoById(userNo)) {
            throw new IllegalAccessException();
        }

        // ????????? ?????? ?????? ??????
        UserMotiInfo userMotiInfo = userMotiInfoRepository.findByUser(
                user.get()
        );

        // ????????? ????????? null????????? ?????????, ????????? ??????????????? ?????????, ????????? 2?????? ????????? 8?????? ????????? ?????????.
        if(message == null || message.contains(" ") || message.length() < 2 || message.length() > 8) {
            throw new IllegalArgumentException();
        }

        // ?????? ?????? ??????
        userMotiInfo.setMotiName(message);

        // ????????? ?????? ?????? ??????
        userMotiInfoRepository.save(userMotiInfo);

        // ?????? ?????? -> ????????? ?????? ??????
        userItemLogRepository.save(UserItemLog.builder()
                .userNo(userNo)
                .timerDate(timerDate.toString())
                .timerTime(timerTime.toString())
                .itemCatalogNo(userInventory.get().getItemCatalog().getItemCatalogNo())
                .countBeforeEvent(userInventory.get().getItemCount())
                .countAfterEvent(userInventory.get().getItemCount() - 1)
                .eventType(EventType.USE)
                .build()
        );
    }

    @Override
    public Optional<UserInventory> findUserInventoryByUser_UserNoAndItemCatalog_ItemCatalogNo(Long user_userNo, Long itemCatalog_itemCatalogNo) {
        return userInventoryRepository.findUserInventoryByUser_UserNoAndItemCatalog_ItemCatalogNo(user_userNo, itemCatalog_itemCatalogNo);
    }
}

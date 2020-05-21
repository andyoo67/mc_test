package com.melchi.order.service;

import com.melchi.order.constant.OrderConstants;
import com.melchi.order.entity.*;
import com.melchi.order.enums.ComEnumType;
import com.melchi.order.mapper.CommonMapper;
import com.melchi.order.mapper.MainMapper;
import com.melchi.order.mapper.RedisMapper;
import com.melchi.order.mapper.ShopMapper;
import com.melchi.order.util.LocationDistance;
import com.melchi.order.web.dto.response.shop.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ShopService {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private MainMapper mainMapper;

    @Autowired
    private CommonMapper commonMapper;

    @Autowired
    private RedisMapper redisMapper;

    public ShopListResponse shopList(String cateNo, String deliAreaNo, Double xPos, Double yPos, String srhArrayCd) throws Exception {

        CateMngEntity cateMngEntity = mainMapper.selectCategoryInfo(cateNo);
        Double viewDist = cateMngEntity.getViewDist();
        List<ShopInfoEntity> entities = shopMapper.selectShopList(cateNo, deliAreaNo);
        List<ShopResponse> safeList = new ArrayList<>();
        List<ShopResponse> rightList = new ArrayList<>();
        List<ShopPhoneResponse> phoneList = new ArrayList<>();

        entities.forEach(entity -> {

            Double distanceInfo = LocationDistance.distance(xPos, yPos, entity.getXPos(), entity.getYPos(), OrderConstants.DISTANCE_UNIT_KM);
            log.info("viewDist==>>{}", viewDist);
            log.info("distanceInfo==>>{}", distanceInfo);

            if(viewDist >= distanceInfo){

                // 매장 현 now 정보 조회 redis
                RedisEntity redisEntity = redisMapper.findById(entity.getShopNo()).orElse(new RedisEntity(entity.getShopNo(), 0,0));
                log.info("findEntity==>>{}", redisEntity);
                if(ComEnumType.SHOP_TYPE_D.getCode().equals(entity.getShopTpCd())){
                    phoneList.add(ShopPhoneResponse.toDTO(entity, redisEntity, distanceInfo));
                } else {

                    if(ComEnumType.SHOP_TYPE_S.getCode().equals(entity.getShopTpCd())){
                        safeList.add(ShopResponse.toDTO(entity, redisEntity, distanceInfo));

                    } else if(ComEnumType.SHOP_TYPE_P.getCode().equals(entity.getShopTpCd())){
                        rightList.add(ShopResponse.toDTO(entity, redisEntity, distanceInfo));
                    }
                }

            }
        });

        // 검색 정렬구분(10:가까운거리순, 20:평점높은순, 30:리뷰많은순, 40:최소주문금액 낮은순, 50:배달비 낮은순, 60:실시간인기 높은순)
        Comparator<ShopResponse> shopResponseComparator;
        Comparator<ShopPhoneResponse> shopPhoneResponseComparator = Comparator.comparing(ShopPhoneResponse::getDistanceInfo);
        if("20".equals(srhArrayCd)){
            shopResponseComparator = Comparator.comparing(ShopResponse::getRviewPt);
            //shopPhoneResponseComparator = Comparator.comparing(ShopPhoneResponse::getRviewPt);
        } else if ("30".equals(srhArrayCd)) {
            shopResponseComparator = Comparator.comparing(ShopResponse::getRviewPt).reversed();
            //shopPhoneResponseComparator = Comparator.comparing(ShopPhoneResponse::getRviewPt).reversed();
        } else if ("40".equals(srhArrayCd)) {
            shopResponseComparator = Comparator.comparing(ShopResponse::getMinOrderAmt);
            //shopPhoneResponseComparator = Comparator.comparing(ShopPhoneResponse::getMinOrder);
        } else if ("50".equals(srhArrayCd)) {
            shopResponseComparator = Comparator.comparing(ShopResponse::getBasicDeliAmt);
            //shopPhoneResponseComparator = Comparator.comparing(ShopPhoneResponse::getBasicDeliAmt);
        } else if ("60".equals(srhArrayCd)) {
            shopResponseComparator = Comparator.comparing(ShopResponse::getNowCnt).reversed();
            //shopPhoneResponseComparator = Comparator.comparing(ShopPhoneResponse::getNowCnt).reversed();
        } else {
            shopResponseComparator = Comparator.comparing(ShopResponse::getDistanceInfo);
            //shopPhoneResponseComparator = Comparator.comparing(ShopPhoneResponse::getDistanceInfo);
        }

        safeList.sort(shopResponseComparator);
        rightList.sort(shopResponseComparator);
        phoneList.sort(shopPhoneResponseComparator);


        return ShopListResponse.toDTO(safeList, rightList, phoneList);
    }

    public ShopInfoResponse shopInfo(String membNo, String shopNo, String deliAreaNo, Double xPos, Double yPos) throws Exception {
        ShopInfoEntity entity = shopMapper.selectShopInfo(membNo, shopNo, deliAreaNo);
        // 매장 등급 이미지
        String gradeImgUrl = shopMapper.selectShopGradeFile(entity.getShopTpCd());
        // 별점 점수
        Integer rviewPoint = shopMapper.selectRviewPt(entity.getShopRview().getRviewPt());
        // 영업상태코드명
        String saleStatCdNm = commonMapper.selectCodeDtlNm(OrderConstants.SALE_STAT_CD, entity.getSaleStatCd());
        // 매장 현 now 정보 조회 redis
        RedisEntity redisEntity = redisMapper.findById(entity.getShopNo()).orElse(new RedisEntity(shopNo, 0,0));
        return ShopInfoResponse.toDTO(entity, redisEntity, gradeImgUrl, rviewPoint, saleStatCdNm);
    }

    public ShopMenuListResponse shopMenuList(String shopNo) throws Exception {

        // 판매상태코드
        List<CdDtlEntity> cdDtlList = commonMapper.selectCodeList(OrderConstants.SELL_STAT_CD);

        // 할인 메뉴
        List<ShopMenuMngEntity> menuDcList = Optional.ofNullable(shopMapper.selectShopGrpDcMenuList(shopNo)).orElseGet(Collections::emptyList);

        // 추천 메뉴
        List<ShopMenuMngEntity> menuRecoList = Optional.ofNullable(shopMapper.selectShopGrpRecoMenuList(shopNo)).orElseGet(Collections::emptyList);

        // 전체 메뉴
        List<ShopGrpEntity> grpList = shopMapper.selectShopGrpList(shopNo);
        grpList.forEach(entity -> {
            List<ShopMenuMngEntity> menuList = Optional.ofNullable(shopMapper.selectShopGrpMenuList(entity.getShopNo(), entity.getMenuGrpNo())).orElseGet(Collections::emptyList);
            entity.setMenuAllList(menuList);
        });
        return ShopMenuListResponse.toDTO(menuDcList, menuRecoList, grpList, cdDtlList);
    }

    public ShopMenuResponse shopMenuInfo(String shopNo, String menuNo, String deliAreaNo) throws Exception {

        // 매장정보
        ShopInfoEntity shopInfoEntity = shopMapper.selectMenuShopInfo( shopNo, deliAreaNo );

        Integer adtSale = shopMapper.selectMenuAdtSaleCount(shopNo, menuNo);
        // 메뉴 상세
        ShopMenuMngEntity shopMenuMngEntity = shopMapper.selectShopMenuInfo(shopNo, menuNo);
        List<ShopSelMenuEntity> selMenuList = null;
        if(shopMenuMngEntity.getShopDcMenu() == null){
            // 선택 메뉴 리스트
            selMenuList = Optional.ofNullable(shopMapper.selectShopSelMenuList(shopNo, menuNo)).orElseGet(Collections::emptyList);
        } else {
            selMenuList = Optional.ofNullable(shopMapper.selectShopDcSelMenuList(shopNo, menuNo)).orElseGet(Collections::emptyList);
        }


        // 매장옵션그룹 리스트
        List<ShopOptCateEntity> optCateList = Optional.ofNullable(shopMapper.selectShopOptCateList(shopNo)).orElseGet(Collections::emptyList);

        for ( ShopOptCateEntity cateEntity: optCateList ) {
            List<ShopMenuOptEntity> menuList = Optional.ofNullable(shopMapper.selectShopMenuOptList(shopNo, menuNo, cateEntity.getOptCateNo())).orElseGet(Collections::emptyList);
            cateEntity.setMenuOptList(menuList);
        }

        return ShopMenuResponse.toDTO(shopInfoEntity, shopMenuMngEntity, selMenuList, optCateList, adtSale);
    }

}

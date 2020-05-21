package com.melchi.order.service;

import com.melchi.order.constant.OrderConstants;
import com.melchi.order.entity.*;
import com.melchi.order.enums.ComEnumType;
import com.melchi.order.enums.SystemMessageCode;
import com.melchi.order.exception.OrderException;
import com.melchi.order.mapper.OrderMapper;
import com.melchi.order.mapper.ShopMapper;
import com.melchi.order.model.OrderResponse;
import com.melchi.order.util.LocationDistance;
import com.melchi.order.util.RandomUtil;
import com.melchi.order.web.dto.request.order.CartSaveRequest;
import com.melchi.order.web.dto.request.order.OrderPreSaveRequest;
import com.melchi.order.web.dto.request.order.OrderSaveRequest;
import com.melchi.order.web.dto.request.order.RviewSaveRequest;
import com.melchi.order.web.dto.response.order.CartListResponse;
import com.melchi.order.web.dto.response.order.OrderPaySaveResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private CommonService commonService;

    @Transactional
    public void baskSave(String membNo, CartSaveRequest cartSaveRequest) throws Exception {

        String cartNo = commonService.getKeyNo(OrderConstants.CART_KEY_NO);

        // 장바구니 저장
        orderMapper.insertCart(cartSaveRequest.toCartEntity(membNo, cartNo));
        // 장바구니 메뉴저장
        orderMapper.insertCartMenu(cartSaveRequest.toCartMenuEntity(membNo, cartNo));
        // 장바구니 메뉴옵션저장
        orderMapper.insertCartOpt(cartSaveRequest.toCartOptEntities(membNo, cartNo));
    }

    public List<CartListResponse> baskList(String membNo, String nearYn, Double xPos, Double yPos, String deliAreaNo) throws Exception {

        List<CartListResponse> cartListResponses = new ArrayList<>();
        List<ShopInfoEntity> cartList = orderMapper.selectShopCartList(membNo, deliAreaNo);

        // 판매상태코드
        List<Integer> totOrderAmtList = null;
        for ( ShopInfoEntity shopInfoEntity : cartList ) {
            String shopNo = shopInfoEntity.getShopNo();
            // 총주문가격
            totOrderAmtList = new ArrayList<>();

            Boolean isDeli = true;// 배달가능여부
            Boolean isSell = true;// 판매여부
            Double distanceInfo = LocationDistance.distance(xPos, yPos, shopInfoEntity.getXPos(), shopInfoEntity.getYPos(), OrderConstants.DISTANCE_UNIT_KM);

            if(ComEnumType.DELI_TARGET_RADIUS.getCode().equals(shopInfoEntity.getDeliTargetCd())){ // 반경

                Double viewDist = orderMapper.selectShopCateDist(shopNo);
                if(viewDist < distanceInfo) isDeli = false;

            } else if (ComEnumType.DELI_TARGET_AREA.getCode().equals(shopInfoEntity.getDeliTargetCd())){ // 지역
                if(null == shopInfoEntity.getDeliArea()) isDeli = false;
            }

            // 주변매장여부 Y
            if("Y".equals(nearYn)){
                if(!isDeli) continue;
            }

            // 메뉴
            List<ShopMenuMngEntity> cartMenuList = orderMapper.selectCartMenuList(shopNo, membNo);

            for ( ShopMenuMngEntity shopMenuMngEntity: cartMenuList ) {

                if(shopMenuMngEntity.getShopDcMenu() != null){
                    totOrderAmtList.add(shopMenuMngEntity.getShopDcMenu().getDcPriceAmt()); // 기본할인가격
                } else {
                    totOrderAmtList.add(shopMenuMngEntity.getMenuPrice()); // 기본가격
                }

                if(!ComEnumType.SELL_STAT_S.getCode().equals(shopMenuMngEntity.getSellStatCd())) isSell = false;

                // 메뉴 옵션 리스트
                List<ShopMenuOptEntity> menuOptList = orderMapper.selectCartMenuOptList(shopNo, shopMenuMngEntity.getCartMenu().getCartNo(), shopMenuMngEntity.getMenuNo(), membNo);
                for (ShopMenuOptEntity shopMenuOptEntity: menuOptList ) {
                    totOrderAmtList.add(shopMenuOptEntity.getOptPrice());
                }
                shopMenuMngEntity.setMenuOptList(menuOptList);
            }
            cartListResponses.add(CartListResponse.toDTO(shopInfoEntity, cartMenuList, isDeli, isSell, totOrderAmtList, distanceInfo));

        } // for cartList
        
        // 거리순
        cartListResponses.sort(Comparator.comparing(CartListResponse::getDistance));
        return cartListResponses;
    }

    @Transactional
    public void baskMenuDel(String membNo, String cartNo, String shopNo) throws Exception {

        orderMapper.deleteCartOpt(cartNo, membNo, "");
        orderMapper.deleteCartMenu(cartNo, membNo, "");
        //메뉴가 없으면 매장 삭제
        String cartMenu = orderMapper.selectCartShopMenu(membNo, shopNo);
        if(cartMenu == null) {
            orderMapper.deleteCart( membNo, shopNo);
        }


    }

    @Transactional
    public void baskDel(String membNo, String shopNo) throws Exception {

        orderMapper.deleteCartOpt("", membNo, shopNo);
        orderMapper.deleteCartMenu("", membNo, shopNo);
        orderMapper.deleteCart( membNo, shopNo);

    }

    //주문체크
    public OrderResponse orderCheck(OrderPreSaveRequest orderPreSaveRequest) throws Exception {

        Boolean isDeli = true;// 배달가능여부
        Boolean isSell = true;// 판매여부

        Double xPos = orderPreSaveRequest.getXPos();
        Double yPos = orderPreSaveRequest.getYPos();
        String shopNo = orderPreSaveRequest.getShopNo();
        String deliAreaNo = orderPreSaveRequest.getDeliAreaNo();
        ShopInfoEntity shopInfoEntity = orderMapper.selectOrderPayShopInfo(shopNo, deliAreaNo);

        Double distanceInfo = LocationDistance.distance(xPos, yPos, shopInfoEntity.getXPos(), shopInfoEntity.getYPos(), OrderConstants.DISTANCE_UNIT_KM);

        if(ComEnumType.DELI_TARGET_RADIUS.getCode().equals(shopInfoEntity.getDeliTargetCd())){ // 반경

            Double viewDist = orderMapper.selectShopCateDist(shopNo);
            if(viewDist < distanceInfo) isDeli = false;

        } else if (ComEnumType.DELI_TARGET_AREA.getCode().equals(shopInfoEntity.getDeliTargetCd())){ // 지역
            if(null == shopInfoEntity.getDeliArea()) isDeli = false;
        }
        if(!isDeli) throw new OrderException(SystemMessageCode.ORDER_CHK_DELI_FAIL);




        if(orderPreSaveRequest.getOrderAmt() < shopInfoEntity.getMinOrderAmt() ) throw new OrderException(SystemMessageCode.ORDER_CHK_MINORDER_FAIL);
        if(!ComEnumType.SALE_STAT_S.getCode().equals(shopInfoEntity.getSaleStatCd())) throw new OrderException(SystemMessageCode.ORDER_CHK_SALE_FAIL);

        List<ShopMenuMngEntity> orderMenuList = orderMapper.selectOrderMenuList(shopNo, orderPreSaveRequest.getMenuList());
        for (ShopMenuMngEntity shopMenuMngEntity : orderMenuList ) {
            if(!ComEnumType.SELL_STAT_S.getCode().equals(shopMenuMngEntity.getSellStatCd())) {
                isSell = false;
                break;
            }
        }

        if(!isSell) throw new OrderException(SystemMessageCode.ORDER_CHK_SELL_FAIL);

        OrderResponse.builder().shopTpCd(shopInfoEntity.getShopTpCd())
                                .shopNo(shopNo)
                                .deliAmt(shopInfoEntity.getBasicDeliAmt() + shopInfoEntity.getDeliArea().getPlusDeliAmt())
                                .build();

        return OrderResponse.builder().shopTpCd(shopInfoEntity.getShopTpCd())
                .shopNo(shopNo)
                .orderCnt(orderPreSaveRequest.getMenuList().size())
                .deliAmt(shopInfoEntity.getBasicDeliAmt() + shopInfoEntity.getDeliArea().getPlusDeliAmt())
                .build();

    }

    @Transactional
    public String preOrderSave(String membNo, OrderPreSaveRequest orderPreSaveRequest) throws Exception {

        OrderResponse orderResponse = orderCheck(orderPreSaveRequest);
        String keyNo = commonService.getKeyNo(OrderConstants.ORDER_KEY_NO);
        List<Integer> orderAmtList = new ArrayList<>();

        String orderNo = keyNo + RandomUtil.randUpString(2);
        // 임시주문 메뉴저장
        List<OrderMenuPreEntity> orderMenuList = new ArrayList<>();
        List<OrderOptPreEntity> orderOptList = new ArrayList<>();
        int orderSeq = 1;
        for (OrderPreSaveRequest.MenuData x: orderPreSaveRequest.getMenuList()) {

            OrderMenuPreEntity entity = new OrderMenuPreEntity();
            entity.setOrderNo(orderNo);
            entity.setOrderSeq(orderSeq);
            entity.setMembNo(membNo);
            entity.setMenuNo(x.getMenuNo());
            entity.setMenuSeq(x.getMenuSeq());
            entity.setPriceAmt(x.getPriceAmt());
            entity.setOrderCnt(x.getOrderCnt());
            entity.setOrderAmt(x.getOrderAmt());
            orderMenuList.add(entity);
            orderAmtList.add(x.getOrderAmt());


            for (OrderPreSaveRequest.OptData o: x.getOptList()) {
                OrderOptPreEntity orderOptTemp = new OrderOptPreEntity();
                orderOptTemp.setMembNo(membNo);
                orderOptTemp.setOrderNo(orderNo);
                orderOptTemp.setOrderSeq(orderSeq);
                orderOptTemp.setOptNo(o.getOptNo());
                orderOptTemp.setOptGrpNo(o.getOptGrpNo());
                orderOptTemp.setOptCnt(x.getOrderCnt());
                orderOptTemp.setOptAmt(o.getOptAmt());
                orderOptList.add(orderOptTemp);
                orderAmtList.add(o.getOptAmt());
            }
            orderSeq++;
        }

        // 임시주문 저장

        orderMapper.insertOrderInfoPre(orderPreSaveRequest.toOrderInfoPreEntity(membNo, orderNo, orderResponse.getDeliAmt(), orderResponse.getShopTpCd(), orderResponse.getOrderCnt()));

        // 임시주문 메뉴저장
        if(orderMenuList.size() > 0 ){
            orderMapper.insertOrderMenuPre(orderMenuList);
        }

        // 임시주문 메뉴옵션저장
        if(orderOptList.size() > 0 ) {
            orderMapper.insertOrderOptPre(orderOptList);
        }

        return orderNo;
    }

    //주문체크
    public void payCheck(String membNo, String orderNo, String deliAreaNo) throws Exception {

        OrderInfoPreEntity orderInfoPre = orderMapper.selectOrderInfoPreInfo(membNo, orderNo);

        if(orderInfoPre == null) throw new OrderException(SystemMessageCode.ORDER_CHK_EMPTY_FAIL);

        String shopNo = orderInfoPre.getShopNo();
        ShopInfoEntity shopInfo = orderMapper.selectOrderPayShopInfo(orderInfoPre.getShopNo(), deliAreaNo);

        // 매장상태 30:전화, 40:비노출
        String shopTpCd = shopInfo.getShopTpCd();
        if("30".equals(shopTpCd) || "40".equals(shopTpCd)){
            throw new OrderException(SystemMessageCode.ORDER_CHK_SHOP_TP_FAIL);
        }

        // 영업상태 10:준비중, 20:영업중
        String saleStatCd = shopInfo.getSaleStatCd();
        if("10".equals(saleStatCd)){
            throw new OrderException(SystemMessageCode.ORDER_CHK_SALE_FAIL);
        }

        Boolean isSell = true; // 판매여부
        Boolean isDisPeriod = true; // 할인기간 여부
        Boolean isPrice = true; // 가격변동 여부
        List<OrderMenuPreEntity> menuPreList = orderMapper.selectOrderMenuPreList(orderNo);
        for (OrderMenuPreEntity orderMenuPre : menuPreList) {
            ShopMenuMngEntity shopMenuMng = null;
            Integer orderAmt = 0;
            if(StringUtils.isEmpty(orderMenuPre.getDcNo())){ // 일반
                shopMenuMng = orderMapper.selectShopMenuMng(orderMenuPre.getMenuNo(), orderMenuPre.getMenuSeq());
                orderAmt = shopMenuMng.getMenuPrice();
            } else { // 할인 메뉴
                shopMenuMng = orderMapper.selectShopDcMenu(orderMenuPre.getMenuNo(), orderMenuPre.getMenuSeq(), orderMenuPre.getDcNo());
                ShopDcMenuEntity shopDcMenu = shopMenuMng.getShopDcMenu();
                orderAmt = shopDcMenu.getDcPriceAmt();

                // 할인 기간 체크
                LocalDate cuDate  = LocalDate.now();
                LocalDate stDate = LocalDate.parse(shopDcMenu.getViewStDay() + shopDcMenu.getViewStHour() + shopDcMenu.getViewStMinu());
                LocalDate edDate = LocalDate.parse(shopDcMenu.getViewEdDay() + shopDcMenu.getViewEdHour() + shopDcMenu.getViewEdMinu());
                if(edDate.isBefore(cuDate)) {
                    isDisPeriod = false;
                }

            }

            // 옵션 리스트
            List<OrderOptPreEntity> optList = orderMapper.selectShopOrderOptList(membNo, shopNo, shopMenuMng.getMenuNo(), orderNo);
            for (OrderOptPreEntity OrderOptPre : optList) {
                log.info("OrderOptPre====>>{}", OrderOptPre.getOptAmt());
                log.info("ShopMenuOpt====>>{}", OrderOptPre.getShopMenuOpt().getOptPrice());
                if(OrderOptPre.getOptAmt() != OrderOptPre.getShopMenuOpt().getOptPrice()){
                    isPrice = false;
                    break;
                }
            }

            // 메뉴상태 10:판매중, 20:일시품절, 30:판매중지
            String sellStatCd = shopMenuMng.getSellStatCd();
            if(!"10".equals(sellStatCd)){
                isSell = false; // 판매여부
            }

            log.info("orderMenuPre.getOrderAmt()====>>{}",orderMenuPre.getOrderAmt());
            log.info("orderAmt====>>{}", orderAmt);
            // 가격변동 메뉴, 옵션, 최소주문 금액(할인전), 배달비
            if(orderMenuPre.getOrderAmt() != orderAmt) {
                isPrice = false;
            }

        } // end for

        // 메뉴상태변경
        if(!isSell) throw new OrderException(SystemMessageCode.ORDER_CHK_SELL_FAIL);
        // 메뉴할인 기간 종료
        if(!isDisPeriod) throw new OrderException(SystemMessageCode.ORDER_CHK_DIS_PER_FAIL);

        // 최소주문 금액
        Integer minOrderAmt = shopInfo.getMinOrderAmt();
        Integer orderAmt = orderInfoPre.getOrderAmt();
        log.info("minOrderAmt====>>{}", minOrderAmt);
        log.info("orderAmt====>>{}", orderAmt);
        if(minOrderAmt > orderAmt) isPrice = false;
        // 배달비
        Integer deliAmt = shopInfo.getBasicDeliAmt() + shopInfo.getDeliArea().getPlusDeliAmt();
        // 주문 배달비
        Integer orderDeliAmt = orderInfoPre.getDeliAmt();
        if(deliAmt != orderDeliAmt) isPrice = false;

        // 가격변동
        if(!isPrice) throw new OrderException(SystemMessageCode.ORDER_CHK_PRICE_FAIL);
    }

    @Transactional
    public OrderInfoPreEntity orderSave(String membNo, OrderSaveRequest orderSaveRequest) throws Exception {

        OrderInfoPreEntity orderInfoPre = orderMapper.selectOrderInfoPre(membNo, orderSaveRequest.getOrderNo(), orderSaveRequest.getPayNo());
        // 운영수수료율
        ChargeMngEntity chargeMng = orderMapper.selectChargeMngInfo(orderInfoPre.getShopInfo().getOperChargeNo(), OrderConstants.OPER_PAY_NO);

        orderMapper.insertOrderInfo(orderSaveRequest.toOrderInfoEntity(membNo, orderInfoPre, chargeMng));
        orderMapper.insertOrderMenu(membNo, orderSaveRequest.getOrderNo());
        orderMapper.insertOrderOpt(membNo, orderSaveRequest.getOrderNo());
        orderMapper.insertCasAgreeInfo(orderSaveRequest.toCasAgreeEntity(membNo));

        return orderInfoPre;
    }

    @Transactional
    public OrderPaySaveResponse orderSaveAfter(String membNo, String shopNo, String orderNo) throws Exception {

        // 장바구니 삭제
        baskDel(membNo, shopNo);

        // 주문정보
        List<OrderInfoEntity> orderInfoList =  Optional.ofNullable(orderMapper.selectOrderPayAfter(membNo, orderNo)).orElseGet(Collections::emptyList);
        OrderInfoEntity orderInfo = orderInfoList.get(0);

        // 메뉴 카운트 증가 shop_info
        for (OrderInfoEntity orderEntity : orderInfoList) {
            orderMapper.updateMenuMngOrderAfter(shopNo, orderEntity.getOrderMenu().getMenuNo(), orderEntity.getOrderMenu().getOrderCnt());
        }

        // 회원 주문카운트 저장
        orderMapper.updateMembOrderCnt(membNo, orderInfo.getOrderAmt());

        Integer orderCnt = orderInfoList.size();
        String menuNo = orderInfo.getOrderMenu().getMenuNo();
        ShopMenuMngEntity menuMng = orderMapper.selectOrderPayAfterMenu(shopNo, menuNo);
        String menuDesc = orderCnt > 1 ? MessageFormat.format(OrderConstants.ORDER_RESULT_MSG, new Object[]{ orderCnt - 1 }) : "";

        return OrderPaySaveResponse.builder()
                .orderNo(orderNo)
                .orderDt(orderInfo.getOrderDt())
                .orderAmt(orderInfo.getOrderAmt() )
                .menuDesc(menuMng.getMenuNm().concat(menuDesc))
                .build();
    }

    public void reviewSave(String membNo, RviewSaveRequest reviewSaveRequest) throws Exception {

        String keyNo = commonService.getKeyNo(OrderConstants.REVIEW_KEY_NO);
        orderMapper.insertShopRview(reviewSaveRequest.toEntity(membNo, keyNo));
        // shop_info 리뷰 점수 업데이트
        shopMapper.updateShopRviewPtInfo(reviewSaveRequest.getShopNo(), reviewSaveRequest.getRviewPt());
    }


}
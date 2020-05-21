package com.melchi.order.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.melchi.order.config.JwtTokenProvider;
import com.melchi.order.constant.OrderConstants;
import com.melchi.order.entity.AppUldInfoEntity;
import com.melchi.order.entity.AppVerInfoEntity;
import com.melchi.order.entity.BannMngEntity;
import com.melchi.order.entity.CateMngEntity;
import com.melchi.order.entity.MemSettingEntity;
import com.melchi.order.entity.TutoEntity;
import com.melchi.order.entity.VisitorInfoEntity;
import com.melchi.order.entity.VisitorLogEntity;
import com.melchi.order.enums.ComEnumType;
import com.melchi.order.mapper.MainMapper;
import com.melchi.order.web.dto.request.main.AppKeyRequest;
import com.melchi.order.web.dto.response.main.AppKeyResponse;
import com.melchi.order.web.dto.response.main.CategoryResponse;

@Service
public class MainService {

    @Autowired
    private MainMapper mainMapper;

    @Autowired
    private CommonService commonService;
    
    @Autowired
    private PubService pubService ;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AppKeyResponse appKey(AppKeyRequest appKeyRequest) throws Exception {

        Boolean isMembReg = false;
        // 앱키 등록여부
        String memberNo = mainMapper.selectMembDevi(appKeyRequest.getDeviId());

        if(StringUtils.isEmpty(memberNo)) {
            memberNo = commonService.getKeyNo(OrderConstants.MEMBER_KEY_NO);
            isMembReg = true;
        }
        // appKey 발행
        String appKey = jwtTokenProvider.createToken(memberNo, appKeyRequest.getDeviId(), appKeyRequest.getDeviTpCd(), "","", "", false);
        mainMapper.insertMembDevi(appKeyRequest.toDeviEntity());
        mainMapper.insertAppKey(memberNo, appKey);
        if(isMembReg) {
            // 비회원정보 insert 
        	mainMapper.insertMembAppReg(appKeyRequest.toEntity(memberNo));
        	// 비회원 카운트 정보 insert
        	mainMapper.insertMembCnt(memberNo);
            // 비회원 환경세팅 정보 insert
        	MemSettingEntity setEntity = new MemSettingEntity();
        	setEntity.setMembNo(memberNo);
        	setEntity.setNoticeYn("Y");
        	setEntity.setEventYn("Y");
        	pubService.saveSetting(setEntity);
        }
        return AppKeyResponse.builder().appKey(appKey).build();
    }

    public AppVerInfoEntity appVer(String appDivCd, String deviTpCd) throws Exception {
        return mainMapper.selectAppVer(appDivCd, deviTpCd);
    }

    public AppUldInfoEntity appUldImg(String membNo, String appDivCd) throws Exception {
    	
    	//회원 최종 로그인 정보 저장
    	mainMapper.updateMembLastDt(membNo);
        List<AppUldInfoEntity> entities = mainMapper.selectAppUldImg(appDivCd);
        Collections.shuffle(entities);
        return entities.size() > 0 ? entities.get(0) : new AppUldInfoEntity();
    }

    public List<TutoEntity> totualImg(String appDivCd) throws Exception {
        return mainMapper.selectAppTutoImg(appDivCd);
    }

    public List<BannMngEntity> banner() throws Exception {
    	//배너 넘길때 첫번째 배너에 대한 노출 수를 카운트 한다.
    	List<BannMngEntity> bannList = mainMapper.selectBanner();
    	
    	if (bannList.size() > 0)
    	{
    		BannMngEntity bannEntity = (BannMngEntity)bannList.get(0);
    		mainMapper.updateBannViewCnt(bannEntity.getBannNo());
    	}
    	
        return bannList;
    }
    
    public void clickviewBanner(String bannNo, String clickviewCd) throws Exception {
    	
    	if ("C".equals(clickviewCd)) {
    		mainMapper.updateBannClickCnt(bannNo);
    	} else if ("V".equals(clickviewCd)) {
    		mainMapper.updateBannViewCnt(bannNo);
    	}
    }
    

    public CategoryResponse category(String cateTpCd) throws Exception {
        List<CateMngEntity> cateBaseList = new ArrayList<CateMngEntity>();
        List<CateMngEntity> catePlanList = new ArrayList<CateMngEntity>();
        if("A".equals(cateTpCd) || "B".equals(cateTpCd)) {
            cateBaseList = Optional.ofNullable(mainMapper.selectCategory("B")).orElseGet(Collections::emptyList);
        }

        if("A".equals(cateTpCd) || "E".equals(cateTpCd)) {
            catePlanList = Optional.ofNullable(mainMapper.selectCategory("E")).orElseGet(Collections::emptyList);
        }
        return CategoryResponse.toDTO(cateBaseList, catePlanList);
    }
    
    /*
     *  방문 로그 정보 저장하기
     */
    @Transactional
    public void saveVisitorLog(String logTpCd, String deviId) throws Exception {

        //오늘 날짜 가져오기
        String visitorDay = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        int logCnt = 0 ;
        int infoCnt = 0 ;
        // logTpCd = '10' 이면 로그 정보 확인 필요
        if (ComEnumType.LOG_TP_LOADING.getCode().equals(logTpCd)) {

            VisitorLogEntity vlogEntity = new VisitorLogEntity();
            vlogEntity.setLogTpCd(logTpCd);
            vlogEntity.setDeviId(deviId);
            vlogEntity.setVisitorDay(visitorDay);
            logCnt = mainMapper.selectVisitorLogCnt(vlogEntity);

            if (logCnt == 0 ){
                mainMapper.insertVisitorLog(vlogEntity);
            }
        }

        //방문수 카운트하기
        VisitorInfoEntity vInfoEntity = new VisitorInfoEntity();
        vInfoEntity.setVisitorDay(visitorDay);
        vInfoEntity.setLogTpCd(logTpCd);


        infoCnt = mainMapper.selectVisitorInfoCnt(vInfoEntity);

        if (ComEnumType.LOG_TP_LOADING.getCode().equals(logTpCd) && logCnt == 0) {
            vInfoEntity.setUvVisitorCnt(1);
        } else {
            vInfoEntity.setUvVisitorCnt(0);
        }

        if (infoCnt == 0) {
            mainMapper.insertVisitorInfo(vInfoEntity);
        } else {
            mainMapper.updateVisitorInfo(vInfoEntity);
        }
    }
    
    
}

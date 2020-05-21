package com.melchi.order.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.melchi.order.constant.OrderConstants;
import com.melchi.order.entity.AppVerInfoEntity;
import com.melchi.order.entity.CdDtlEntity;
import com.melchi.order.entity.FileEntity;
import com.melchi.order.entity.MemSettingEntity;
import com.melchi.order.entity.MembInfoEntity;
import com.melchi.order.entity.OneAskEntity;
import com.melchi.order.entity.ReadChkEntity;
import com.melchi.order.enums.SystemMessageCode;
import com.melchi.order.exception.OrderException;
import com.melchi.order.mapper.CommonMapper;
import com.melchi.order.mapper.CustMapper;
import com.melchi.order.mapper.MainMapper;
import com.melchi.order.mapper.PubMapper;
import com.melchi.order.model.TokenResponse;
import com.melchi.order.web.dto.request.cust.MembDelivRequest;
import com.melchi.order.web.dto.request.cust.MembSignUpRequest;
import com.melchi.order.web.dto.request.cust.OneAskRequest;
import com.melchi.order.web.dto.response.cust.MyInfoResponse;
import com.melchi.order.web.dto.response.cust.MyTitleInfoResponse;
import com.melchi.order.web.dto.response.cust.OneAskResponse;
import com.melchi.order.web.dto.response.cust.SignUpResponse;

@Service
public class CustService {

    @Autowired
    private CustMapper custMapper;
    
    @Autowired
    private CommonMapper commonMapper;
    
    @Autowired
    private MainMapper mainMapper;
    
    @Autowired
    private PubMapper pubMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CommonService commonService;
    
    @Autowired
    private PubService pubService;

    public void saveDeliv(String memberNo, MembDelivRequest membDelivRequest) throws Exception {
        //String areaCd = custMapper.selectDeliAreaNo(membDelivRequest.getAreaInfo());
        custMapper.insertMemDeliLo(membDelivRequest.toEntity(memberNo));
    }
    
    @Transactional
    public SignUpResponse signUp(TokenResponse appKeyInfo, MembSignUpRequest membSignUpRequest, String appKey) throws Exception {

        LocalDateTime membJoinDt = LocalDateTime.now();
        String memberNo = commonService.getKeyNo(OrderConstants.MEMBER_KEY_NO);
        membSignUpRequest.setPw(passwordEncoder.encode(membSignUpRequest.getPw()));

        custMapper.insertMemberSignUp(membSignUpRequest.toEntity(appKeyInfo.getAppDiviId(), memberNo, membJoinDt,membSignUpRequest.getSmsEmailYn() ));
        mainMapper.insertMembCnt(memberNo);
        // 회원 환경설정 정보 저장하기
        MemSettingEntity setEntity = new MemSettingEntity();
    	setEntity.setMembNo(memberNo);
    	setEntity.setNoticeYn("Y");
    	setEntity.setOrderStatusYn("Y");
    	setEntity.setReviewWYn("Y");
    	setEntity.setReqAnsYn("Y");
    	setEntity.setReqAnsYn("Y");
    	setEntity.setNoticeYn("Y");
    	setEntity.setEventYn("N");
    	pubService.saveSetting(setEntity);
        

        return SignUpResponse.builder().email(membSignUpRequest.getEmail()).phoneNo(membSignUpRequest.getPhoneNo()).membJoinDt(membJoinDt).build();
    }
    
    public OneAskResponse selectOneAsk(String membNo) throws Exception {
    	
    	// 판매상태코드
        List<CdDtlEntity> cdDtlList = commonMapper.selectCodeList(OrderConstants.ASK_STAT_CD);
        
        List<CdDtlEntity> cdDtlAskList = commonMapper.selectCodeList(OrderConstants.ASK_CD);
    	
    	// 데이타 조회
    	List<OneAskEntity> migEntity = new ArrayList<OneAskEntity>();
    	OneAskEntity tmpEntity = null;
    	
    	List<OneAskEntity> entityList = custMapper.selectOneAsk(membNo);
    	
    	if (entityList != null) { 
    		String askNo = "" ;
    		
    		for (int i = 0 ; i < entityList.size() ; i++) {
        		OneAskEntity entity = (OneAskEntity)entityList.get(i);
        		
        		if (askNo.equals(entity.getAskNo())) {  // 동일시
        			// 파일 체크
        			FileEntity fileEntity  = new FileEntity();
    				fileEntity.setFileSeq( entity.getFile().getFileSeq());
    				fileEntity.setFilePath( entity.getFile().getFilePath());
    				tmpEntity.getFileList().add(fileEntity);
    				
        		} else {
        			if (tmpEntity != null) {
        				migEntity.add(tmpEntity);
        			}
        			// 새로운 row 이면서 이미지 파일 존재여부
        			askNo = entity.getAskNo() ;
        			tmpEntity = new OneAskEntity();
        			tmpEntity.setAskNo(entity.getAskNo());
        			tmpEntity.setAskCd(entity.getAskCd());
        			tmpEntity.setAskTitl(entity.getAskTitl());
        			tmpEntity.setAskStatCd(entity.getAskStatCd());
        			tmpEntity.setAskDesc(entity.getAskDesc());
        			tmpEntity.setReplayAddDt(entity.getReplayAddDt());
        			tmpEntity.setReplyDesc(entity.getReplyDesc());
        			tmpEntity.setReplayAnsDt(entity.getReplayAnsDt());
        			tmpEntity.setImgFileNo(entity.getImgFileNo());
        			
        			if (!StringUtils.isEmpty(entity.getImgFileNo())) {
        				FileEntity fileEntity  = new FileEntity();
        				fileEntity.setFileSeq( entity.getFile().getFileSeq());
        				fileEntity.setFilePath( entity.getFile().getFilePath());
        				tmpEntity.getFileList().add(fileEntity);
        			}
        		}
        		
        		//  마지막row 일경우 마무리작업
    			if (i == (entityList.size()-1))
        		{
        			migEntity.add(tmpEntity);
        		}
        	}
    	}
    	
    	
    	int cnt = pubMapper.selectReadCnt(membNo, OrderConstants.READ_ONEASK);
    	
    	if (cnt == 0 ) {
    		pubMapper.insertReadChk(membNo, OrderConstants.READ_ONEASK);
    	} else {
    		pubMapper.updateReadChk(membNo, OrderConstants.READ_ONEASK);
    	}
    	
        return OneAskResponse.toDTO( migEntity,cdDtlList, cdDtlAskList );
    }
    
    public void saveOneAsk( String membNo, OneAskRequest entity) throws Exception {
    	
    	OneAskEntity oneaskEntity = new OneAskEntity();
    	oneaskEntity.setAskNo(commonService.getKeyNo(OrderConstants.ASK_KEY_NO));
    	oneaskEntity.setMembNo(membNo);
    	oneaskEntity.setAskId(custMapper.selectEmail(membNo));
    	oneaskEntity.setAskCd(entity.getAskCd());
    	oneaskEntity.setAskTitl(entity.getAskTitl());
    	oneaskEntity.setAskDesc(entity.getAskDesc());
    	oneaskEntity.setImgFileNo(entity.getImgFileNo());
    	custMapper.insertOneAsk(oneaskEntity);
    }
    
    
    public MyTitleInfoResponse selectMyTotInfo (String membNo, String deviTpCd) throws Exception {
    	// 내 개인정보 가져오기 
    	MembInfoEntity myEntity =   custMapper.selectMyInfo(membNo);
    	
    	// 앱버전 정보 가져오기
 	    AppVerInfoEntity appEntity = mainMapper.selectAppVer(OrderConstants.APP_DIV_CD, deviTpCd);
    	
    	List<ReadChkEntity>  readEntity = new ArrayList<ReadChkEntity>() ;
    	
    	// 공지 이벤트 1:1 관련 최신 일자 가져옴
    	List<ReadChkEntity> readSetList =  custMapper.selectReadSetInfo(membNo);
    	
    	// 현 읽음상태의 최신날짜를 가져옴
    	List<ReadChkEntity> readChkList = custMapper.selectReadChkInfo(membNo);
    	
    	if (readSetList != null) {
    		for (int i = 0 ; i < readSetList.size() ; i++) {
    			ReadChkEntity entity = readSetList.get(i);
    			entity.setReadYn("Y");
    			if ( readChkList == null) {
    				// 읽지 않음 상태
    				entity.setReadYn("N");
    			} else {
    				// 비교해서 읽음상태로 변경 
    				for (int j = 0 ; j < readChkList.size() ; j++) {
    					
    					ReadChkEntity chkEntity = readChkList.get(j);
    					if (chkEntity.getReadTargetCd().equals(entity.getReadTargetCd()) 
    							 &&  (chkEntity.getReadDt().isBefore(entity.getReadDt()) || chkEntity.getReadDt().isEqual(entity.getReadDt()) )  ) {
    						entity.setReadYn("N");
    						break;
    					}
    				}
    			}
    			
    			readEntity.add(entity);
    		}
    	}
    	
    	// 공지 이벤트 1:1 결과값 존재 여부 파악함
    	// 공지 체크 
    	String nticeChk = "N";
    	for (int n = 0 ; n < readEntity.size() ; n++) {
    		ReadChkEntity entity = readEntity.get(n);
    		if ("10".equals(entity.getReadTargetCd()) && entity.getReadYn() != null && "".equals(entity.getReadYn()) ) {
    			nticeChk = "Y";
    		}
		}
    	
    	// 이벤트 체크 
    	String eveChk = "N";
    	for (int n = 0 ; n < readEntity.size() ; n++) {
    		ReadChkEntity entity = readEntity.get(n);
    		if ("20".equals(entity.getReadTargetCd())  && entity.getReadYn() != null && "".equals(entity.getReadYn())) {
    			eveChk = "Y";
    		}
		}
    	
    	// 1:1 체크 
    	String oneChk = "N";
    	for (int n = 0 ; n < readEntity.size() ; n++) {
    		ReadChkEntity entity = readEntity.get(n);
    		if ("30".equals(entity.getReadTargetCd())  && entity.getReadYn() != null && "".equals(entity.getReadYn())) {
    			oneChk = "Y";
    		}
		}
    	
    	
    	if ("N".equals(nticeChk)) {
    		ReadChkEntity tmpEntity = new ReadChkEntity();
    		tmpEntity.setReadTargetCd("10");
    		tmpEntity.setReadYn("Y");
    		readEntity.add(tmpEntity);
    	}
    	
    	if ("N".equals(eveChk)) {
    		ReadChkEntity tmpEntity = new ReadChkEntity();
    		tmpEntity.setReadTargetCd("20");
    		tmpEntity.setReadYn("Y");
    		readEntity.add(tmpEntity);
    	}
    	
    	if ("N".equals(oneChk)) {
    		ReadChkEntity tmpEntity = new ReadChkEntity();
    		tmpEntity.setReadTargetCd("30");
    		tmpEntity.setReadYn("Y");
    		readEntity.add(tmpEntity);
    	}
    	
    	// 결과물 처리 함
    	
    	return MyTitleInfoResponse.toDTO( myEntity,readEntity, appEntity);
    	
    }
    
    public MyInfoResponse selectMyInfo(String membNo) throws Exception {
    	
    	MembInfoEntity entity = custMapper.selectMyDtlInfo(membNo);
    	
    	List<CdDtlEntity> cdDtlAskList = commonMapper.selectCodeList(OrderConstants.ADT_AUTH_CD);
    	
    	return MyInfoResponse.toDTO( entity,cdDtlAskList);
    }
    
    //insertPhoneHist
    public void saveMyInfo( String membNo, String saveType,  String chgData ) throws Exception {
    	
    	MembInfoEntity entity = new MembInfoEntity();
    	
    	entity.setMembNo(membNo);
    	
    	if ("10".equals(saveType)) {  // 닉네임변경
    		entity.setNickNm(chgData);
    	} else if ("20".equals(saveType)) {  // 이메일수신
    		entity.setEmailRcvYn(chgData);
    	} else if ("30".equals(saveType)) {  // sms수신
    		entity.setSmsRcvYn(chgData);
    	} else if ("40".equals(saveType)) {  // 핸드폰번호
    		entity.setPhoneNo(chgData);
    		// 이력저장
    		custMapper.insertPhoneHist(entity);
    	} else if ("50".equals(saveType)) {  // 성인인증
    		if ("Y".equals(chgData)) {
    			entity.setAdtAuthCd(OrderConstants.ADT_AUTH_OK);
    		}
    	} else if ("60".equals(saveType)) {  // pw
    		//암호화 처리 후 저장
    		entity.setPw(chgData);
    	}
    	
    	custMapper.updateMyInfo(entity);
    }
}

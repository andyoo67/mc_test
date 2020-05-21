package com.melchi.order.service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import com.melchi.order.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.melchi.order.constant.OrderConstants;
import com.melchi.order.enums.SystemMessageCode;
import com.melchi.order.exception.OrderException;
import com.melchi.order.mapper.MainMapper;
import com.melchi.order.mapper.PubMapper;
import com.melchi.order.model.TokenResponse;
import com.melchi.order.util.RandomUtil;
import com.melchi.order.web.dto.request.pub.CasAgreeRequest;
import com.melchi.order.web.dto.response.pub.MainNotiEvenResponse;
import com.melchi.order.web.dto.response.pub.MembSettingResponse;
import com.melchi.order.web.dto.response.pub.QuesListResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class PubService {

    @Autowired
    private PubMapper pubMapper;
    
    @Autowired
	private MainMapper mainMapper;

	@Autowired
	private CommonService commonService;

    
    @Value("${defaultinfo.pageNo}")
	private int pageNo;
    
    @Value("${defaultinfo.pageCnt}")
	private int pageCnt;

    public List<CasMngEntity> terms(String casTyCd) throws Exception {
    	
    	List<CasMngEntity> casList = pubMapper.selectTermsList(casTyCd);
    	
    	if ("10".equals(casTyCd))
    	{
    		// 마케팅정보 수신 동의 (선택) 항목 추가
    		CasMngEntity markEntity = new CasMngEntity();
    		markEntity.setCasNo("M");
    		markEntity.setCasNm(OrderConstants.MARKETING_RECEPTION);
    		markEntity.setCasDesc(OrderConstants.MARKETING_RECEPTION_DESC);
    		casList.add(markEntity);
    	}
    	
        return casList;
        
        
    }

    public MainNotiEvenResponse mainNoti() throws Exception {
        NticeEntity nticeEntity = pubMapper.selectMainNtice();
        EvenMngEntity evenMngEntity = pubMapper.selectMainEvent();
        return MainNotiEvenResponse.toDTO(nticeEntity, evenMngEntity);
    }

    public CompanyEntity company() throws Exception {
        return  pubMapper.selectCompany();
    }

    public List<PopuMngEntity> popup() throws Exception {
        
    	List<PopuMngEntity> entityList = pubMapper.selectPopup();
    	
        if (entityList.size() > 0)
    	{
        	PopuMngEntity popEntity = (PopuMngEntity)entityList.get(0);
        	//pubMapper.updatePopViewCnt(popEntity.getPopuNo());
        	saveClickView( OrderConstants.POP_JOB, OrderConstants.VIEW_JOB, popEntity.getPopuNo());
    	}
        
        return entityList;
    }
    
    /*
    public void clickviewPop(String popuNo, String clickviewCd) throws Exception {
    	
    	if ("C".equals(clickviewCd)) {
    		pubMapper.updatePopClickCnt(popuNo);
    	} else if ("V".equals(clickviewCd)) {
    		pubMapper.updatePopViewCnt(popuNo);
    	}
    }
    */
    
    
    public void saveterms( String memberNo, String carTyCd, List<CasAgreeRequest> casAgreeList) throws Exception {
    	if ("10".equals(carTyCd)) {
    		
    		String eventYn = "N";
    		
    		for (int i = 0 ; i < casAgreeList.size() ; i++)
    		{
    			CasAgreeRequest entity = (CasAgreeRequest)casAgreeList.get(i);
    			if ( "M".equals(entity.getCasNo()))
				{
    				/*  회원가입시 해당 폰 마케팅수신동의값 전달 받아 처리함.
    				MembInfoEntity memEntity = new MembInfoEntity();
    				memEntity.setMembNo(memberNo);
    				
    				if ("Y".equals(entity.getAgreeYn())) {
    					// 마케팅동의정보를 회원 이메일 sms 에 동의 Y로 업데이타 함
    					eventYn = "Y";
    					memEntity.setSmsRcvYn("Y");
    					memEntity.setEmailRcvYn("Y");
    				} else {
    					memEntity.setSmsRcvYn("N");
    					memEntity.setEmailRcvYn("N");
    				}
    				
    				pubMapper.updateMembMarketingAgree(memEntity);
    				*/
    				casAgreeList.remove(i);
    				break;
				}
    		}
    		
    		//최초 접근시에는 환결설정을 세팅함.
    		int setCnt = pubMapper.selectMembSettingCnt(memberNo);
    		
    		MemSettingEntity setEntity = new MemSettingEntity();
    		setEntity.setMembNo(memberNo);
    		setEntity.setNoticeYn("Y");
    		setEntity.setEventYn("Y");
    		if (setCnt == 0) {
    			// 최초 등록 세팅 비회원값으로 세팅  공지알림과 이벤트 알림
    			pubMapper.insertMembSetting(setEntity);
    		} else {
    			// 기존 비회원 값으로 세팅
    			pubMapper.updateMembSetting(setEntity);
    		}
    	}
    	pubMapper.insertMembCasAre(memberNo, casAgreeList.stream().map(dto -> CasAgreeRequest.toEntity(dto)).collect(Collectors.toList()));
    }
    
    /*
     *  인증번호 요청하기
     */
    @Transactional
    public MemCertNoEntity transCert(String membNo, String deviId, String phoneNo, String regMemYn) throws Exception {
    	MemCertNoEntity retEntity = new MemCertNoEntity();

    	//오늘 날짜 가져오기
    	String certDt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        
    	// 회원가입일경우 전화번호 가능 여부 체크
    	if ("Y".equals(regMemYn)) {
    		int dupCnt = pubMapper.selectPhoneDupCnt(phoneNo);
    		
    		if ( dupCnt > 0 ) {
    			// 오류처리
    			System.out.println("=====================================================");
        		throw new OrderException(SystemMessageCode.CHK_PHONE_ERR);
    		}
    	}
    	
        //모델 생성
        MemCertNoEntity entity = new MemCertNoEntity();
        entity.setMembNo(membNo);
        entity.setDeviId(deviId);
        entity.setPhoneNo(phoneNo);
        entity.setCertDt(certDt);

        //당일 인증 certNum max값을 가져옴  0 이면 신규, 0보다 크면 체크 필요
        int maxCertNum = pubMapper.selectMemCertMaxNum(entity);
        String certNo = "";
        String sendOk = "N";
        int    certNum = 0 ;

        if (maxCertNum == 0 ) {
        	//신규
        	certNum = 1 ;
        	sendOk = "Y";
        } else {
        	entity.setCertNum(maxCertNum);
        	/* 당일 인증 요청수 체크 */
        	int reqCnt = pubMapper.selectReSendCnt(entity);

        	if (reqCnt == 0 ) {
        		certNum = maxCertNum + 1 ;
        		sendOk = "Y";
        	} else {
        		/* 1분이내 체크 */
        		int oneMinCnt = pubMapper.selectReSend60SecondCnt(entity);
        		if (oneMinCnt == 0 ) {
        			certNum = maxCertNum + 1 ;
        			sendOk = "Y";
        		} else {
        			// 재생성 금지 오류 발생 처리
        			sendOk = "N";
        		}
        	}
        }

        // 오류인지 체크하여 발행할지 오류처리할지 판단.
    	if ("Y".equals(sendOk)) {
    		certNo = RandomUtil.randInt(1000, 9999) + "";
    		entity.setCertNum(certNum);
    		entity.setCertNo(certNo);
    		pubMapper.insertMemCertNo(entity);

    		// sms 문자 발송처리 [(가칭)멸치배달] 인증번호는 0000 입니다.
    		//SmsSendReqModel smsSendReqModel = new SmsSendReqModel();
    		//smsSendReqModel.setTitle("휴대폰 인증");
    		//smsSendReqModel.setMessage("[" + brandMst.getBrand_nm() + "]\r\n휴대폰 인증번호는 " + params.getCertified_no() + "입니다.\r\n정확히 입력해주세요.");
    		//smsSendReqModel.setCallback_num(brandMst.getManager_tel());
    		//smsSendReqModel.setPhone_num(params.getUser_phone());
    		/* dwkim sms 정의 되는 시점에 처리 진행함 */

    		//결과값 return
    		retEntity.setCertDt(certDt);
    		retEntity.setCertNum(certNum);

    	} else {
    		// 오류발생처리함
    		System.out.println("=====================================================");
    		throw new OrderException(SystemMessageCode.SMSREQ_ERR);
    	}
        	

    	return retEntity;
    }
    
    
    /*
     *  인증번호 확인 하기
     */
    @Transactional
    public void confCert(String membNo, String deviId, String phoneNo, String certNo, String certDt, int certNum) throws Exception {
       

		MemCertNoEntity entity = new MemCertNoEntity();
		// 인증 번호 확인
		entity.setMembNo(membNo);
		entity.setDeviId(deviId);
		entity.setPhoneNo(phoneNo);
		entity.setCertDt(certDt);
		entity.setCertNum(certNum);
		entity.setCertNo(certNo);

		int certOutCnt = pubMapper.selectConf3MinCnt(entity);
		if (certOutCnt > 0) {
			throw new OrderException(SystemMessageCode.SMSOUT_ERR);
		}

		int certCnt =  pubMapper.selectMemCertChkCnt(entity);

		if (certCnt == 0 ) {
			//인증 오륲
			throw new OrderException(SystemMessageCode.SMSCONF_ERR);
		} else {
			//인증 성공
			pubMapper.updateMemCertNo(entity);
		}

		    
    }
    
    
    /*
     *  공지사항리스트 
     */
    public List<NticeEntity> selectNticeList(String membNo) throws Exception {
    	
    	int cnt = pubMapper.selectReadCnt(membNo, OrderConstants.READ_NTICE);
    	
    	if (cnt == 0 ) {
    		pubMapper.insertReadChk(membNo, OrderConstants.READ_NTICE);
    	} else {
    		pubMapper.updateReadChk(membNo, OrderConstants.READ_NTICE);
    	}
    	
         return pubMapper.selectNticeList();
    }
    
    /*
     *  공지 상세 
     */
    public NticeEntity selectNtice(String membNo, String nticeNo) throws Exception {
    	
    	int cnt = pubMapper.selectReadCnt(membNo, OrderConstants.READ_NTICE);
    	
    	if (cnt == 0 ) {
    		pubMapper.insertReadChk(membNo, OrderConstants.READ_NTICE);
    	} else {
    		pubMapper.updateReadChk(membNo, OrderConstants.READ_NTICE);
    	}
    	
    	pubMapper.updateNticeViewCnt(nticeNo);
    	
         return pubMapper.selectNtice(nticeNo);
    }
    
    /*
     *  이벤트리스트 
     */
    public List<EvenMngEntity> selectEventList(String membNo) throws Exception {
    	int cnt = pubMapper.selectReadCnt(membNo, OrderConstants.READ_EVEN);
    	
    	if (cnt == 0 ) {
    		pubMapper.insertReadChk(membNo, OrderConstants.READ_EVEN);
    	} else {
    		pubMapper.updateReadChk(membNo, OrderConstants.READ_EVEN);
    	}
    	
    	
         return pubMapper.selectEventList();
    }
    
    /*
    *  이벤트상세
    */
   public EvenMngEntity selectEvent(String evenNo, String membNo) throws Exception {
	   
	   int cnt = pubMapper.selectReadCnt(membNo, OrderConstants.READ_EVEN);
   	
	   	if (cnt == 0 ) {
	   		pubMapper.insertReadChk(membNo, OrderConstants.READ_EVEN);
	   	} else {
	   		pubMapper.updateReadChk(membNo, OrderConstants.READ_EVEN);
	   	}
	   
	   pubMapper.updateEventViewCnt(evenNo);
        return pubMapper.selectEvent(evenNo);
   }
   
   /*
    * 자주묻는질문 그룹가져오기
    */
   public QuesListResponse selectQues() throws Exception {
	   
	   List<FreqAskCateInfoEntity> grpList = pubMapper.selectFaqAskCate();
	   List<FreqAskEntity> recoList = pubMapper.selectFaqAsk();
	   List<FreqAskEntity> top5List = pubMapper.selectFaqTop5();
	   
	   
	   
	   for (int i = 0 ; i < top5List.size() ; i++)
	   {
		   FreqAskEntity entity = (FreqAskEntity) top5List.get(i);
		   
		   switch(entity.getFreqTop().getViewNum()){
	        case 1: 
	        	entity.setQuesDesc("01 " + entity.getQuesDesc());
	            break;
	        case 2:
	        	entity.setQuesDesc("02 " + entity.getQuesDesc());
	            break;
	        case 3 :
	        	entity.setQuesDesc("03 " + entity.getQuesDesc());
	            break;
	        case 4 :	
	        	entity.setQuesDesc("04 " + entity.getQuesDesc());
	            break;
	        case 5 :
		        entity.setQuesDesc("05 " + entity.getQuesDesc());
	            break;
	        default :
	            break;
		   }
		   
	   }
	   
	   top5List.addAll(recoList);

	   return QuesListResponse.toDTO( grpList,top5List );
   }
   
   /*
    * 자주묻는질문 상세클릭 카운트
    */
   /*
   public void clickQues( String quesNo) throws Exception {
   	   	pubMapper.updateQuesViewCnt(quesNo);
   }
   */
   
   /*
    * 환경설정 정보 가져오기
    */
   public MembSettingResponse selectMembSetting(String membNo, String deviTpCd) throws Exception {
	   
	   int cnt = pubMapper.selectMembSettingCnt(membNo);
	   
	   MemSettingEntity entity = null;
	   
	   if (cnt == 0 ) {
		   entity = new MemSettingEntity();
		   entity.setCommYn("N");
		   entity.setEventYn("N");
		   entity.setMembNo(membNo);
		   entity.setNoticeYn("N");
		   entity.setOrderStatusYn("N");
		   entity.setReqAnsYn("N");
		   entity.setReviewWYn("N");
	   } else {
		   entity = pubMapper.selectMembSetting(membNo);
	   }
	   
	   // 앱버전 정보 가져오기
	   AppVerInfoEntity appEntity = mainMapper.selectAppVer(OrderConstants.APP_DIV_CD, deviTpCd);
	   
	   return MembSettingResponse.toDTO(entity, appEntity);
	   
   }
   
   
   /*
    * 환경설정 저장하기
    */
   public void saveSetting(MemSettingEntity entity) throws Exception {
	   //이미 등록 여부 확인
	   int setCnt = pubMapper.selectMembSettingCnt(entity.getMembNo());
	   
	   if (setCnt == 0 ) {
		   pubMapper.insertMembSetting(entity);
	   } else {
		   pubMapper.updateMembSetting(entity);
	   }
   }
   
   /*
    * 개별 환경설정 저장하기
    */
   public void saveUnitSetting(TokenResponse tokenResponse, String setType, String setYn) throws Exception {
	   MemSettingEntity entity = new MemSettingEntity();
	   entity.setMembNo(tokenResponse.getMemberNo());
	   
	   switch(setType){
       case "10": 
    	   entity.setOrderStatusYn(setYn);	
           break;
       case "20":
       	   entity.setReviewWYn(setYn);
           break;
       case "30" :
       	   entity.setCommYn(setYn);
           break;
       case "40" :	
       	   entity.setReqAnsYn(setYn);
           break;
       case "50" :
	       entity.setNoticeYn(setYn);
           break;
       case "60" :
	       entity.setEventYn(setYn);
           break;
       default :
           break;
	   }
	  // 회원여부  tokenResponse.getIsLogin()
	   int setCnt = pubMapper.selectMembSettingCnt(entity.getMembNo());
	   
	   if (setCnt == 0 ) {
		   pubMapper.insertUpMembSetting(entity);   
	   } else {
		 //환경정보 저장
		   pubMapper.updateMembSetting(entity);   
	   }
	   
	   
   }
    
   /*
    * 자주묻는질문 조회수  10
	   공지사항 조회수     ==> 상세 전달시 처리
		이벤트 조회수      ==> 이벤트 상세가져오기에서 처리
		배너 클릭수 , 노출수  ==> 최소 배너 리스트 전달시 첫번째거 노출 카운트 하고 보냄
		                이외는 배너 클릭 및 노출은 호출함
		팝업 클릭수 , 노출수 ==> 최소 배너 리스트 전달시 첫번째거 노출 카운트 하고 보냄
		                이외는 배너 클릭 및 노출은 호출함
		                
      jobType  10 자주묻는질문  
		       20 배너
			   30 팝업
		runType  10 클릭
		         20 노출
		relNo 관련번호		               
    */
   public void saveClickView( String jobType, String runType, String relNo) throws Exception {
	    if ("10".equals(jobType)) { // 자주묻는질문
	    	pubMapper.saveQuesCnt(relNo);
	    } else if ("20".equals(jobType)) { // 배너
	    	pubMapper.saveBannCnt(runType, relNo);
	    } else if ("30".equals(jobType)) { // 팝업
	    	pubMapper.savePopCnt(runType, relNo);
	    }
   }

	public void fileUpload(String membNo, MultipartFile multipartFile) throws Exception {

		//String keyNo = commonService.getKeyNo(OrderConstants.FILE_KEY_NO);
/*
		log.info("getOriginalFilename==={}", multipartFile.getOriginalFilename());
		Integer seq = 1;

		byte[] bytes = multipartFile.getBytes();
		Path path = Paths.get(uri);
		Files.write(path, bytes);

		FileEntity fileEntity = new FileEntity();
		//for (MultipartFile multipartFile : files) {
			byte[] bytes = multipartFile.getBytes();
			Path path = Paths.get(uri);
			Files.write(path, bytes);

			String originalFilename = multipartFile.getOriginalFilename();
			String extName = originalFilename.substring(originalFilename.lastIndexOf("."), originalFilename.length());
			fileEntity.setFileNo(keyNo);
			fileEntity.setFileSeq(seq);
			fileEntity.setFileOrgNm(multipartFile.getOriginalFilename());
			fileEntity.setFileNm(membNo);
			fileEntity.setFilePath("");
			fileEntity.setFileSize(multipartFile.getSize());
			seq++;
		//}
		//pubMapper.insertReViewFile();
		*/


	}
   
}

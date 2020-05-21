package com.melchi.order.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.melchi.order.entity.MembSrhAddrEntity;
import com.melchi.order.entity.MembSrhWordEntity;
import com.melchi.order.enums.ComEnumType;
import com.melchi.order.mapper.SearchMapper;
import com.melchi.order.web.dto.request.search.MembSrhAddrRequest;

@Service
public class SearchService {

    @Autowired
    private SearchMapper searchMapper;

    public List<MembSrhWordEntity> searchWord(String membNo) throws Exception {
        return searchMapper.selectMembSrhWordList(membNo);
    }

    public void saveSearchWord(String memberNo, String srhWord) throws Exception {
        searchMapper.insertMembSrhWord(memberNo, srhWord);
        searchMapper.deleteMembSrhWord(memberNo, null, ComEnumType.WORD_DEL_SAVE.getCode());
    }

    public void delSearchWord(String memberNo, String srhWord) throws Exception {
        searchMapper.deleteMembSrhWord(memberNo, srhWord, ComEnumType.WORD_DEL_ONE.getCode());
    }

    public void delAllSearchWord(String memberNo) throws Exception {
        searchMapper.deleteMembSrhWord(memberNo, null, ComEnumType.WORD_DEL_ALL.getCode());
    }
    
    public List<MembSrhAddrEntity> searchAddr(String membNo) throws Exception {
        return searchMapper.selectMembSrhAddrList(membNo);
    }
    
    public void saveSearchAddr(String membNo, MembSrhAddrRequest entity) throws Exception {
    	
    	MembSrhAddrEntity membSrhAddrEntity = new MembSrhAddrEntity();
    	
    	membSrhAddrEntity.setMembNo(membNo);
    	membSrhAddrEntity.setAddrSeq(searchMapper.selectMembSrhAddrMaxSeq(membNo)+1);
    	membSrhAddrEntity.setAddr(entity.getAddr());
    	membSrhAddrEntity.setOldAddr(entity.getOldAddr());
    	membSrhAddrEntity.setDeliAreaNo(entity.getDeliAreaNo());
    	membSrhAddrEntity.setXPos(entity.getXPos());
    	membSrhAddrEntity.setYPos(entity.getYPos());
    	
    	// 현 건수 조회
    	int cnt = searchMapper.selectMembSrhAddrCnt(membNo);
    	if (cnt >= 20) {
    		int minSeq = searchMapper.selectMembSrhAddrMinSeq(membNo);
    		searchMapper.deleteMembSrhAddr(membNo,minSeq, ComEnumType.WORD_DEL_ONE.getCode());  
    	} 
    	
    	searchMapper.insertMembSrhAddr(membSrhAddrEntity);
    }
    
    public void delSearchAddr(String memberNo, int addrSeq, String delType) throws Exception {
        searchMapper.deleteMembSrhAddr(memberNo, addrSeq, delType);
    }

}

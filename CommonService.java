package com.melchi.order.service;

import com.melchi.order.config.JwtTokenProvider;
import com.melchi.order.entity.AppVerInfoEntity;
import com.melchi.order.entity.FileEntity;
import com.melchi.order.entity.MembSrhWordEntity;
import com.melchi.order.entity.PayMethodEntity;
import com.melchi.order.mapper.CommonMapper;
import com.melchi.order.mapper.MainMapper;
import com.melchi.order.mapper.SearchMapper;
import com.melchi.order.web.dto.request.main.AppKeyRequest;
import com.melchi.order.web.dto.response.main.AppKeyResponse;
import com.melchi.order.web.dto.response.order.PayMethodResponse;
import com.melchi.order.web.dto.response.search.MembSrhWordResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommonService {

    @Autowired
    private CommonMapper commonMapper;

    public String getKeyNo(String numNo) throws Exception {
        return commonMapper.selectKeyNo(numNo);
    }

    public List<PayMethodEntity> payMethod() throws Exception {
        return commonMapper.selectPayMethod();
    }
}

package com.melchi.order.service;

import com.melchi.order.config.JwtTokenProvider;
import com.melchi.order.entity.MemberEntity;
import com.melchi.order.entity.RedisEntity;
import com.melchi.order.entity.RedisRsaEntity;
import com.melchi.order.mapper.MainMapper;
import com.melchi.order.mapper.MemberMapper;
import com.melchi.order.mapper.RedisMapper;
import com.melchi.order.mapper.RedisRsaMapper;
import com.melchi.order.model.TokenResponse;
import com.melchi.order.util.RSAUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class RedisService {

    @Autowired
    private RedisRsaMapper redisRsaMapper;


    public String rsaSave(String memberNo) throws Exception {
        Map<String, String> keypair =RSAUtil.createKeypairAsString();
        RedisRsaEntity entity = new RedisRsaEntity(memberNo, keypair.get("privateKey"));
        redisRsaMapper.save(entity);
        return keypair.get("publicKey");
    }


}

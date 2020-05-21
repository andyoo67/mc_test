package com.melchi.order.service;

import com.melchi.order.config.JwtTokenProvider;
import com.melchi.order.entity.MemberEntity;
import com.melchi.order.entity.RedisEntity;
import com.melchi.order.entity.RedisRsaEntity;
import com.melchi.order.enums.SystemMessageCode;
import com.melchi.order.exception.OrderException;
import com.melchi.order.mapper.MainMapper;
import com.melchi.order.mapper.MemberMapper;
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
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class SigninService implements UserDetailsService {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private MainMapper mainMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    RedisRsaMapper redisRsaMapper;

    public void emailCheck(String email) throws Exception {
        Integer cnt = memberMapper.selectEmailCheck(email);
        if(cnt > 0){
            throw new OrderException(SystemMessageCode.EMAIL_CHK_FAIL);
        }
    }

    public MemberEntity signin(HttpSession session, String email, String password, TokenResponse tokenResponse) throws Exception {

        RedisRsaEntity findEntity = redisRsaMapper.findById(tokenResponse.getMemberNo()).orElse(new RedisRsaEntity());
        String decPasswd = RSAUtil.decrypt(password, findEntity.getPraivateKey());
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(email, decPasswd);
        Authentication authentication = authenticationManager.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        MemberEntity memberEntity = (MemberEntity) authentication.getPrincipal();

        String appKey = jwtTokenProvider.createToken(memberEntity.getMembNo()
                                                    , tokenResponse.getAppDiviId()
                                                    , ""
                                                    , ""
                                                    , ""
                                                    , ""
                                                    , true);
        memberEntity.setAppKey(appKey);

        // appKey 저장
        mainMapper.insertAppKey(memberEntity.getMembNo(), appKey);
        memberMapper.updateMembLoginDt(memberEntity.getMembNo());
        return memberEntity;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null){
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }

    }

    public UserDetails getMember(String email) {
        MemberEntity entity = memberMapper.selectMemberByEmail(email);
        entity.setAuthorities(getAuthorities());
        return entity;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return getMember(username);
    }

    public Collection<GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_MELCHI_ORDER_MEMBER"));
        return authorities;
    }

    public PasswordEncoder passwordEncoder() {
        return this.passwordEncoder;
    }

}

package com.capstone.learning_squad_be.controller;

import com.capstone.learning_squad_be.domain.enums.ErrorCode;
import com.capstone.learning_squad_be.domain.user.User;
import com.capstone.learning_squad_be.dto.oauth.TokensReturnDto;
import com.capstone.learning_squad_be.dto.common.ReturnDto;
import com.capstone.learning_squad_be.dto.user.UserJoinRequestDto;
import com.capstone.learning_squad_be.dto.user.UserLoginRequestDto;
import com.capstone.learning_squad_be.dto.user.UserNickNameRequestDto;
import com.capstone.learning_squad_be.dto.user.UserTokenReturnDto;
import com.capstone.learning_squad_be.exception.AppException;
import com.capstone.learning_squad_be.jwt.JwtService;
import com.capstone.learning_squad_be.oauth.OAuthLoginService;
import com.capstone.learning_squad_be.oauth.kakao.KakaoLoginParams;
import com.capstone.learning_squad_be.repository.user.UserRepository;
import com.capstone.learning_squad_be.security.CustomUserDetail;
import com.capstone.learning_squad_be.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OAuthLoginService oAuthLoginService;

    @PostMapping("/join")
    public ReturnDto<Void> join(@RequestBody UserJoinRequestDto dto){
        userService.join(dto);
        return ReturnDto.ok();
    }

    @PostMapping("/login")
    @ResponseBody
    public ReturnDto<UserTokenReturnDto> login(@RequestBody UserLoginRequestDto dto, HttpServletResponse response){
        String accessToken = userService.login(dto.getUserName(), dto.getPassword());

        // Refresh Token 생성
        String refreshToken = jwtService.getRefreshToken(dto.getUserName());

        Cookie cookie = new Cookie("refreshToken", refreshToken);

        // 만료 7일
        cookie.setMaxAge(7 * 24 * 60 * 60);

        // 쿠키 옵션
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setPath("/");

        // response
        response.addCookie(cookie);

        UserTokenReturnDto returnDto = UserTokenReturnDto.builder().token(accessToken).build();

        return ReturnDto.ok(returnDto);
    }

    @PostMapping("login/oauth/kakao")
    public ReturnDto<UserTokenReturnDto> loginKakao(@RequestBody KakaoLoginParams params, HttpServletResponse response) {
        log.info("controller get kakao login request");

        TokensReturnDto dto = oAuthLoginService.getTokens(params);

        String accessToken = dto.getAccessToken();
        String refreshToken = dto.getRefreshToken();

        Cookie cookie = new Cookie("refreshToken", refreshToken);

        // 만료 7일
        cookie.setMaxAge(7 * 24 * 60 * 60);

        // 쿠키 옵션
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setPath("/");

        // response
        response.addCookie(cookie);

        UserTokenReturnDto returnDto = UserTokenReturnDto.builder().token(accessToken).build();
        return ReturnDto.ok(returnDto);
    }

    @PatchMapping("/updateNickName")
    public ReturnDto<User> updateNickName(@RequestBody UserNickNameRequestDto dto, @AuthenticationPrincipal CustomUserDetail customUserDetail) {
        log.info("update start");
        // 현재 userName
        String userName = customUserDetail.getUser().getUserName();
        log.info("userName:{}",userName);

        User user = userService.updateNickName(userName, dto.getNickName());

        return ReturnDto.ok(user);
    }

    @GetMapping("/info")
    public ReturnDto<User> info(@AuthenticationPrincipal CustomUserDetail customUserDetail){
        //userName 추출
        String userName = customUserDetail.getUser().getUserName();

        User user = userRepository.findByUserName(userName)
                .orElseThrow(()->new AppException(ErrorCode.USERNAME_NOT_FOUND, "사용자" + userName + "이 없습니다."));

        return ReturnDto.ok(user);
    }

    @PostMapping("/refresh")
    public ReturnDto<UserTokenReturnDto> refresh (HttpServletRequest request) {

        // 쿠키에서 Refresh Token 추출
        Cookie[] cookies = request.getCookies();
        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
                break;
            }
        }

        //userName 추출
        String userName = jwtService.getUserNameByRefreshToken(refreshToken);
        String newAccessToken = null;

        log.info("refreshToken:{}",refreshToken);
        log.info("userName:{}",userName);

        if (jwtService.validateRefreshToken(refreshToken)) {
            // 새로운 Access Token 발급
            newAccessToken = jwtService.getAccessToken(userName);// 새로운 Access Token 생성 로직
        }

        UserTokenReturnDto returnDto = UserTokenReturnDto.builder().token(newAccessToken).build();

        return ReturnDto.ok(returnDto);

    }

}

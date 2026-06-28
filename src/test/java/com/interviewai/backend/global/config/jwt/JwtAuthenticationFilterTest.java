package com.interviewai.backend.global.config.jwt;

import com.interviewai.backend.auth.enums.AuthErrorCode;
import com.interviewai.backend.global.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthenticationFilter 테스트")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("기능_테스트_Authorization_헤더가_없으면_인증_없이_필터가_통과된다")
    void 기능_테스트_Authorization_헤더가_없으면_인증_없이_필터가_통과된다() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("기능_테스트_유효한_Bearer_토큰이면_SecurityContext에_userId가_설정된다")
    void 기능_테스트_유효한_Bearer_토큰이면_SecurityContext에_userId가_설정된다() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token.here");
        when(jwtProvider.validateToken("valid.token.here")).thenReturn(true);
        when(jwtProvider.getUserId("valid.token.here")).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("기능_테스트_토큰_검증_실패이면_인증_없이_필터가_통과된다")
    void 기능_테스트_토큰_검증_실패이면_인증_없이_필터가_통과된다() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token");
        when(jwtProvider.validateToken("invalid.token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("기능_테스트_validateToken에서_BusinessException이_발생하면_인증_없이_필터가_통과된다")
    void 기능_테스트_validateToken에서_BusinessException이_발생하면_인증_없이_필터가_통과된다() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired.token");
        when(jwtProvider.validateToken("expired.token"))
                .thenThrow(new BusinessException(AuthErrorCode.EXPIRED_TOKEN));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}

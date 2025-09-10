package com.muscat.commonlib.util;

import com.muscat.commonlib.constants.CommonConstants;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 공통 검증 유틸리티
 */
@Slf4j
public final class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(CommonConstants.EMAIL_PATTERN);
    private static final Pattern PHONE_PATTERN = Pattern.compile(CommonConstants.PHONE_PATTERN);

    private ValidationUtils() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    /**
     * 문자열이 null이거나 빈 문자열인지 검증
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 문자열이 null이 아니고 비어있지 않은지 검증
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 이메일 형식 검증
     */
    public static boolean isValidEmail(String email) {
        if (isEmpty(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 전화번호 형식 검증
     */
    public static boolean isValidPhone(String phone) {
        if (isEmpty(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 비밀번호 길이 검증
     */
    public static boolean isValidPasswordLength(String password) {
        if (isEmpty(password)) {
            return false;
        }
        int length = password.length();
        return length >= CommonConstants.MIN_PASSWORD_LENGTH && length <= CommonConstants.MAX_PASSWORD_LENGTH;
    }

    /**
     * 양수 검증
     */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 0 이상 검증
     */
    public static boolean isNonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * 날짜 범위 검증 (from <= to)
     */
    public static boolean isValidDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return false;
        }
        return !to.isBefore(from);
    }

    /**
     * 날짜시간 범위 검증 (from <= to)
     */
    public static boolean isValidDateTimeRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return false;
        }
        return !to.isBefore(from);
    }

    /**
     * ID 값 검증 (양수)
     */
    public static boolean isValidId(Long id) {
        return id != null && id > 0;
    }

    /**
     * 페이지 번호 검증
     */
    public static boolean isValidPage(Integer page) {
        return page != null && page >= 0;
    }

    /**
     * 페이지 크기 검증
     */
    public static boolean isValidPageSize(Integer size) {
        return size != null && size > 0 && size <= CommonConstants.MAX_PAGE_SIZE;
    }

    /**
     * 통화 코드 검증
     */
    public static boolean isValidCurrency(String currency) {
        if (isEmpty(currency)) {
            return false;
        }
        return CommonConstants.CURRENCY_KRW.equals(currency) || 
               CommonConstants.CURRENCY_USD.equals(currency);
    }
}
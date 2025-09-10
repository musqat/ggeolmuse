package com.muscat.commonlib.exception;

import com.muscat.commonlib.util.ProblemDetailUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BaseExceptionHandler {


  protected ResponseEntity<ProblemDetail> handleBaseException(BaseException ex, HttpServletRequest request) {
    log.error("BaseException 발생: {}", ex.getMessage(), ex);
    
    ProblemDetail problem = ProblemDetailUtils.createBadRequestProblem(
        ex.getErrorMessage(), 
        ex.getErrorCode(), 
        request.getRequestURI()
    );
    problem.setTitle("Business Logic Error");
    
    return ResponseEntity.badRequest().body(problem);
  }

  protected ResponseEntity<ProblemDetail> handleServiceException(ServiceException ex, HttpServletRequest request) {
    log.error("ServiceException 발생: {}", ex.getMessage(), ex);
    
    ProblemDetail problem = ProblemDetailUtils.createProblem(
        HttpStatus.INTERNAL_SERVER_ERROR, 
        ex.getErrorMessage(), 
        ex.getErrorCode(), 
        request.getRequestURI(),
        "Service Error"
    );
    
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }

  protected ResponseEntity<ProblemDetail> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    
    log.error("Validation 오류 발생: {}", ex.getMessage());
    
    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult().getAllErrors().forEach(error -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      validationErrors.put(fieldName, errorMessage);
    });
    
    ProblemDetail problem = ProblemDetailUtils.createValidationProblem(
        "입력값 검증에 실패했습니다",
        request.getRequestURI(),
        validationErrors
    );
    
    return ResponseEntity.badRequest().body(problem);
  }

  protected ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, HttpServletRequest request) {
    log.error("예상치 못한 오류 발생: {}", ex.getMessage(), ex);
    
    ProblemDetail problem = ProblemDetailUtils.createInternalServerError(request.getRequestURI());
    
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }
}
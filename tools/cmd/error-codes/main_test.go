package main

import (
	"slices"
	"testing"
)

const enumPath = "trade-service/src/main/java/com/muscat/trade/common/enums/response/TradeResponse.java"

const enumSource = `package com.muscat.trade.common.enums.response;

public enum TradeResponse implements ErrorCode {
  // === 클라이언트 오류 ===
  INVALID_QUANTITY("수량은 0보다 큰 정수여야 합니다.", HttpStatus.BAD_REQUEST),
  MARKET_CLOSED("장이 닫혔습니다.", HttpStatus.BAD_REQUEST),
  TRADE_NOT_FOUND(
    "거래를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

  private final String message;
  private final HttpStatus status;
}
`

const exceptionSource = `package com.muscat.trade.common.exception;

public class TradeException extends BusinessException {
  public TradeException(TradeResponse response) {
    super(response);
  }
}
`

func sourcesWith(others map[string]string) map[string]string {
	sources := map[string]string{
		enumPath: enumSource,
		"trade-service/src/main/java/com/muscat/trade/common/exception/TradeException.java": exceptionSource,
	}
	for name, text := range others {
		sources["trade-service/src/main/java/com/muscat/trade/"+name+".java"] = text
	}
	return sources
}

func messages(found []finding) []string {
	var out []string
	for _, f := range found {
		out = append(out, f.message)
	}
	return out
}

func TestUnusedConstants(t *testing.T) {
	cases := []struct {
		name    string
		sources map[string]string
		want    []string
	}{
		{"쓰는 곳 없는 상수를 알림", sourcesWith(map[string]string{
			"Service": "throw new TradeException(TradeResponse.INVALID_QUANTITY);",
			"Lookup":  "import static com.muscat.trade.common.enums.response.TradeResponse.*;\nthrow of(TRADE_NOT_FOUND);",
		}), []string{"TradeResponse.MARKET_CLOSED 를 쓰는 곳 없음"}},
		{"enum 이름 없이 같은 이름만 나오면 쓰는 곳이 아님", sourcesWith(map[string]string{
			"Service": "TradeResponse.INVALID_QUANTITY; TradeResponse.TRADE_NOT_FOUND;",
			"Other":   "enum MarketState { MARKET_CLOSED }",
		}), []string{"TradeResponse.MARKET_CLOSED 를 쓰는 곳 없음"}},
		{"테스트 소스에서만 쓰면 쓰는 곳이 아님", func() map[string]string {
			s := sourcesWith(map[string]string{"Service": "TradeResponse.INVALID_QUANTITY; TradeResponse.TRADE_NOT_FOUND;"})
			s["trade-service/src/test/java/com/muscat/trade/ServiceTest.java"] = "assertThat(TradeResponse.MARKET_CLOSED)"
			return s
		}(), []string{"TradeResponse.MARKET_CLOSED 를 쓰는 곳 없음"}},
		{"상수 뒤 필드는 상수가 아님", sourcesWith(map[string]string{
			"Service": "TradeResponse.INVALID_QUANTITY TradeResponse.MARKET_CLOSED",
		}), []string{"TradeResponse.TRADE_NOT_FOUND 를 쓰는 곳 없음"}},
		{"ErrorCode 를 구현한 enum 은 자리와 상관없이 봄", func() map[string]string {
			s := sourcesWith(map[string]string{
				"Service": "TradeResponse.INVALID_QUANTITY TradeResponse.MARKET_CLOSED TradeResponse.TRADE_NOT_FOUND CommonResponse.USED",
			})
			s["ggeolmuse-common/src/main/java/com/muscat/commonlib/exception/CommonResponse.java"] =
				"public enum CommonResponse implements ErrorCode {\n  USED(\"a\", X),\n  LEFT(\"b\", X);\n}"
			return s
		}(), []string{"CommonResponse.LEFT 를 쓰는 곳 없음"}},
		{"빌드 산출물 · 테스트 소스의 enum 은 보지 않음", func() map[string]string {
			s := sourcesWith(map[string]string{"Service": "TradeResponse.INVALID_QUANTITY TradeResponse.MARKET_CLOSED TradeResponse.TRADE_NOT_FOUND"})
			s["trade-service/target/classes/com/muscat/trade/common/enums/response/OldResponse.java"] = "public enum OldResponse { GONE(\"\") ; }"
			s["trade-service/src/test/java/com/muscat/trade/common/enums/response/FakeResponse.java"] = "public enum FakeResponse { FAKE(\"\") ; }"
			return s
		}(), nil},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := messages(unusedConstants(c.sources)); !slices.Equal(got, c.want) {
				t.Errorf("unusedConstants = %v, 기대 %v", got, c.want)
			}
		})
	}
}

func TestExceptionConstructions(t *testing.T) {
	used := "TradeResponse.INVALID_QUANTITY TradeResponse.MARKET_CLOSED TradeResponse.TRADE_NOT_FOUND\n"
	cases := []struct {
		name string
		code string
		want []string
	}{
		{"오류 코드와 원인 예외만 넘기면 통과",
			"throw new TradeException(TradeResponse.MARKET_CLOSED);\nthrow new TradeException(TradeResponse.TRADE_NOT_FOUND, e);", nil},
		{"백엔드에 선언하지 않은 예외는 표준 · 라이브러리 예외로 알림",
			"throw new IllegalStateException(e);\nthrow new AssertionError();",
			[]string{"IllegalStateException 대신 서비스 예외와 오류 코드를 씀", "AssertionError 대신 서비스 예외와 오류 코드를 씀"}},
		{"문자열 문구를 넘기면 알림",
			"throw new TradeException(TradeResponse.MARKET_CLOSED, \"장 마감 (\" + day + \")\");",
			[]string{"TradeException 에 문자열 문구를 넘김"}},
		{"예외가 아닌 오류 설명 객체(OAuth2Error 등)는 세지 않음",
			"OAuth2TokenValidatorResult.failure(new OAuth2Error(\"invalid_token\", \"x\", null));", nil},
		{"주석 · 문자열 안의 예외 생성은 세지 않음",
			"// throw new IllegalStateException(\"x\");\n/* new RuntimeException(\"y\") */\nString s = \"new IllegalArgumentException(\";", nil},
		{"문자열 속 괄호 · 따옴표가 있어도 생성식 끝을 찾음",
			"log.warn(\"(\\\"\"); throw new TradeException(TradeResponse.MARKET_CLOSED); String t = \")\";", nil},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			sources := sourcesWith(map[string]string{"Service": used + c.code})
			sources["trade-service/src/test/java/com/muscat/trade/ServiceTest.java"] = "throw new IllegalStateException(\"테스트는 보지 않음\");"
			if got := messages(exceptionConstructions(sources)); !slices.Equal(got, c.want) {
				t.Errorf("exceptionConstructions = %v, 기대 %v", got, c.want)
			}
		})
	}
}

func TestLineNumber(t *testing.T) {
	sources := sourcesWith(map[string]string{"Service": "a\nb\n  throw new IllegalStateException();"})
	found := exceptionConstructions(sources)
	if len(found) != 1 || found[0].line != 3 {
		t.Fatalf("줄 번호 = %+v, 기대 3", found)
	}
}

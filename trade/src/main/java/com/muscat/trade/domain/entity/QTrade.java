package com.muscat.trade.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QTrade extends EntityPathBase<Trade> {

    private static final long serialVersionUID = -12327005L;

    public static final QTrade trade = new QTrade("trade");

    public final StringPath accountId = createString("accountId");

    public final DateTimePath<LocalDateTime> createdAt = createDateTime("createdAt", LocalDateTime.class);

    public final DateTimePath<LocalDateTime> executedAt = createDateTime("executedAt", LocalDateTime.class);

    public final NumberPath<BigDecimal> fee = createNumber("fee", BigDecimal.class);

    public final NumberPath<BigDecimal> price = createNumber("price", BigDecimal.class);

    public final NumberPath<BigDecimal> quantity = createNumber("quantity", BigDecimal.class);

    public final StringPath symbol = createString("symbol");

    public final NumberPath<BigDecimal> totalAmount = createNumber("totalAmount", BigDecimal.class);

    public final DatePath<LocalDate> tradeDate = createDate("tradeDate", LocalDate.class);

    public final StringPath tradeId = createString("tradeId");

    public final EnumPath<com.muscat.trade.common.enums.type.TradeType> tradeType = createEnum("tradeType", com.muscat.trade.common.enums.type.TradeType.class);

    public final StringPath userId = createString("userId");

    public QTrade(String variable) {
        super(Trade.class, forVariable(variable));
    }

    public QTrade(Path<? extends Trade> path) {
        super(path.getType(), path.getMetadata());
    }

    public QTrade(PathMetadata metadata) {
        super(Trade.class, metadata);
    }

}
